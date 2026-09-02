package com.prideuniversal.domain.bill;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.domain.bill.dto.BillDtos.BillResponse;
import com.prideuniversal.domain.bill.dto.BillDtos.GenerationResult;
import com.prideuniversal.domain.bill.dto.BillDtos.MarkPaidRequest;
import com.prideuniversal.domain.bill.dto.BillDtos.OverdueSweepResult;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.settings.WingSettingsService;
import com.prideuniversal.domain.settings.WingSettingsService.BillingRates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Maintenance billing: generation, payment, and the overdue sweep.
 *
 * <p>This is where money is recorded, so every operation here is written to be
 * safe when it is run twice — by an impatient double-click, a retried request,
 * or a scheduler that fired late.
 */
@Service
public class MaintenanceBillService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceBillService.class);

    /** Mirrors {@code Role.FINANCE}: who may touch the society's money. */
    private static final String FINANCE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER')";

    /** The society is in India; "today" must not depend on the server's locale. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MaintenanceBillRepository billRepository;
    private final FlatRepository flatRepository;
    private final WingSettingsService settingsService;

    public MaintenanceBillService(MaintenanceBillRepository billRepository,
                                  FlatRepository flatRepository,
                                  WingSettingsService settingsService) {
        this.billRepository = billRepository;
        this.flatRepository = flatRepository;
        this.settingsService = settingsService;
    }

    // =====================================================================
    // Generation
    // =====================================================================

    /**
     * Raises one bill per active flat for a billing period.
     *
     * <h3>Why this is safe to run twice</h3>
     * Three layers, innermost last:
     * <ol>
     *   <li>It reads which flats are already billed for the period and skips
     *       them, so a second run creates nothing and reports it as
     *       {@code skipped} rather than failing.</li>
     *   <li>The whole run is one transaction, so a failure part-way leaves no
     *       half-generated month behind.</li>
     *   <li>{@code uq_bill_flat_period} in the database. If two admins press
     *       the button simultaneously, both pass step 1 — the check and the
     *       insert are not atomic together — and the loser's transaction is
     *       rejected by the constraint and rolled back whole. The unique index
     *       is the actual guarantee; step 1 only makes the common case
     *       pleasant.</li>
     * </ol>
     */
    @PreAuthorize(FINANCE)
    @Transactional
    public GenerationResult generate(int year, int month) {
        requireBillablePeriod(year, month);

        List<Flat> flats = flatRepository.findAllByActiveTrueOrderByWingAscFlatNumberAsc();
        if (flats.isEmpty()) {
            throw ApiException.badRequest("There are no active flats to bill.");
        }

        Set<UUID> alreadyBilled = new HashSet<>(billRepository.findBilledFlatIds(year, month));

        List<MaintenanceBill> fresh = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Flat flat : flats) {
            if (alreadyBilled.contains(flat.getId())) {
                continue;
            }
            // Cached per wing, so this is one lookup per wing and not per flat.
            BillingRates rates = settingsService.ratesFor(flat.getWing());

            MaintenanceBill bill = new MaintenanceBill();
            bill.setFlat(flat);
            // Copied, not joined - see the note on MaintenanceBill about why a
            // bill must keep saying who owed the money at the time.
            bill.setWing(flat.getWing());
            bill.setFlatNumber(flat.getFlatNumber());
            bill.setOwnerName(flat.getOwnerName());

            bill.setYear(year);
            bill.setMonth(month);
            bill.setAmount(rates.monthlyTotal());
            bill.setLateFee(BigDecimal.ZERO);
            bill.setStatus(BillStatus.PENDING);
            // due_day is constrained to 1..28, so this is valid in February too.
            bill.setDueDate(LocalDate.of(year, month, rates.dueDay()));

            fresh.add(bill);
            total = total.add(rates.monthlyTotal());
        }

        billRepository.saveAll(fresh);

        log.info("Generated {} bills for {}, skipped {} already billed",
                fresh.size(), YearMonth.of(year, month), flats.size() - fresh.size());

        return new GenerationResult(year, month, fresh.size(), flats.size() - fresh.size(), total);
    }

    // =====================================================================
    // Payment
    // =====================================================================

    /**
     * Records payment against a bill.
     *
     * <h3>The concurrency this exists to stop</h3>
     * Two committee members open the same unpaid bill and both click "mark
     * paid" for the same cash handover. Without a guard both writes succeed,
     * the second overwrites the first's payment reference, and the society has
     * one payment recorded under two receipt numbers.
     *
     * <p>Three things prevent it, and they catch different cases:
     * <ul>
     *   <li>the <b>already-paid check</b> — the friendly one, for a second
     *       attempt made minutes later;</li>
     *   <li>the <b>version comparison</b> — for a stale screen, caught before
     *       any write;</li>
     *   <li>the <b>{@code @Version} column at flush</b> — for a genuine race,
     *       where both requests read the same version and neither check could
     *       have seen the other.</li>
     * </ul>
     * Only the last is a guarantee. The first two exist to make the common
     * cases produce a clear message instead of a raw conflict.
     */
    @PreAuthorize(FINANCE)
    @Transactional
    public BillResponse markPaid(UUID id, MarkPaidRequest request) {
        MaintenanceBill bill = findOrThrow(id);
        requireCurrentVersion(bill, request.version());

        if (bill.getStatus() == BillStatus.PAID) {
            throw ApiException.conflict(
                    "This bill was already marked paid on " + bill.getPaidDate()
                            + " (receipt " + bill.getReceiptNumber() + ").");
        }
        if (bill.getStatus() == BillStatus.WAIVED) {
            throw ApiException.conflict("This bill was waived and cannot be marked paid.");
        }

        LocalDate today = LocalDate.now(IST);
        LocalDate paidDate = request.paidDate() != null ? request.paidDate() : today;
        if (paidDate.isAfter(today)) {
            throw ApiException.badRequest("A payment date cannot be in the future.");
        }

        bill.setStatus(BillStatus.PAID);
        bill.setPaidDate(paidDate);
        bill.setPaymentMode(request.paymentMode());
        bill.setPaymentReference(blankToNull(request.paymentReference()));
        bill.setReceiptNumber(receiptNumberFor(bill, request));

        // saveAndFlush so the response carries the incremented version - see
        // FlatService.update for what a stale version echo does to a client.
        return BillResponse.from(billRepository.saveAndFlush(bill));
    }

    // =====================================================================
    // Overdue sweep
    // =====================================================================

    /**
     * Moves pending bills past their due date to {@code overdue} and applies
     * the wing's late fee.
     *
     * <p>Deliberately only {@code PENDING -> OVERDUE}. A bill that is already
     * overdue is not picked up again, so running this twice in one day — or
     * catching up after the server was down for a week — cannot charge the
     * late fee twice. The status transition is what makes the job idempotent;
     * a query on "due date has passed" alone would compound the fee nightly.
     *
     * <p>Not annotated with {@code @PreAuthorize}: the scheduler calls it with
     * no security context, and an authorisation check would fail every night.
     * Human callers reach it through {@link #runOverdueSweep()}, which is
     * guarded. This is the one place in the codebase where the "rules live on
     * the service" pattern needs a deliberate exception, so it is worth being
     * explicit about which method is the public door and which is internal.
     */
    @Transactional
    public int applyOverdue(LocalDate asOf) {
        List<MaintenanceBill> due =
                billRepository.findByStatusAndDueDateBefore(BillStatus.PENDING, asOf);

        for (MaintenanceBill bill : due) {
            bill.setStatus(BillStatus.OVERDUE);
            bill.setLateFee(settingsService.ratesFor(bill.getWing()).lateFee());
        }
        billRepository.saveAll(due);

        if (!due.isEmpty()) {
            log.info("Marked {} bills overdue as of {}", due.size(), asOf);
        }
        return due.size();
    }

    /**
     * Manual trigger, for when the committee does not want to wait for 02:30.
     *
     * <p>{@code @Transactional} here is not decoration. This method calls
     * {@link #applyOverdue} on {@code this}, and a self-invocation does not go
     * through Spring's proxy — the annotation on the callee would be ignored
     * and the sweep would run with no transaction of its own. Declaring it on
     * the entry point means one transaction spans the whole sweep, which is
     * what we want anyway: either every bill moves to overdue or none does.
     * (Same trap, opposite resolution, as {@code OtpAttemptRecorder} — there
     * the fix was a separate bean because a <em>different</em> transaction was
     * needed.)
     */
    @PreAuthorize("hasAnyRole('ADMIN','CHAIRMAN')")
    @Transactional
    public OverdueSweepResult runOverdueSweep() {
        LocalDate today = LocalDate.now(IST);
        return new OverdueSweepResult(today, applyOverdue(today));
    }

    // =====================================================================
    // Reads
    // =====================================================================

    /** The billing register. Committee only — this is every flat's data. */
    @PreAuthorize(FINANCE)
    @Transactional(readOnly = true)
    public List<BillResponse> search(Integer year, Integer month, String wing, BillStatus status) {
        return billRepository
                .search(year, month, wing == null ? null : wing.trim().toUpperCase(), status)
                .stream()
                .map(BillResponse::from)
                .toList();
    }

    /** A resident's own bills. No role needed; the flat comes from the token. */
    @Transactional(readOnly = true)
    public List<BillResponse> myBills() {
        AuthPrincipal caller = AuthPrincipal.current();
        if (caller.flatId() == null) {
            throw ApiException.badRequest(
                    "Your account is not linked to a flat, so it has no bills.");
        }
        return billRepository.findByFlat_IdOrderByYearDescMonthDesc(caller.flatId())
                .stream()
                .map(BillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BillResponse detail(UUID id) {
        MaintenanceBill bill = findOrThrow(id);

        // getFlatId(), not getFlat().getId() - the read-only column mapping
        // avoids initialising the lazy association just to compare an id.
        if (!AuthPrincipal.current().canAccessFlat(bill.getFlatId())) {
            throw ApiException.forbidden("You can only view your own flat's bills.");
        }
        return BillResponse.from(bill);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private MaintenanceBill findOrThrow(UUID id) {
        return billRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bill", id));
    }

    private void requireCurrentVersion(MaintenanceBill bill, Long clientVersion) {
        if (!Objects.equals(bill.getVersion(), clientVersion)) {
            throw new OptimisticLockingFailureException(
                    "Bill " + bill.getId() + " was modified by someone else");
        }
    }

    /**
     * Mirrors {@code ck_bill_month} / {@code ck_bill_year}, and refuses to bill
     * far into the future — generating for 2087 by mistyping a year is a
     * plausible slip, and unpicking it means deleting rows by hand.
     */
    private void requireBillablePeriod(int year, int month) {
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("Month must be between 1 and 12.");
        }
        if (year < 2000 || year > 2100) {
            throw ApiException.badRequest("Year is out of range.");
        }
        YearMonth requested = YearMonth.of(year, month);
        YearMonth limit = YearMonth.now(IST).plusMonths(1);
        if (requested.isAfter(limit)) {
            throw ApiException.badRequest(
                    "Bills can only be generated up to one month ahead. "
                            + "The latest allowed period is " + limit + ".");
        }
    }

    /**
     * Uses the supplied receipt number, or mints one.
     *
     * <p>The generated form is unique but <em>not</em> sequential. A society
     * audit usually wants a gapless series, which needs a database sequence
     * (or a numbers table) rather than a random suffix — deliberately deferred
     * rather than faked, because a series with gaps looks worse than no series
     * at all. {@code receipt_number} is UNIQUE in the schema either way.
     */
    private String receiptNumberFor(MaintenanceBill bill, MarkPaidRequest request) {
        String supplied = blankToNull(request.receiptNumber());
        if (supplied != null) {
            return supplied;
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "RCPT-%d%02d-%s".formatted(bill.getYear(), bill.getMonth(), suffix);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

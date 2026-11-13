package com.prideuniversal.domain.transaction;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerEntryResponse;
import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerPage;
import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The society's books: read and kept by the finance roles (admin, chairman,
 * secretary, treasurer), as {@code SecurityConfig} fences {@code /api/transactions}.
 */
@Service
public class LedgerService {

    private static final String CAN_READ = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER')";
    private static final String CAN_WRITE = CAN_READ;

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @PreAuthorize(CAN_READ)
    @Transactional(readOnly = true)
    public LedgerPage page(String wing, Integer year, Integer month) {
        String w = normaliseWing(wing);
        BigDecimal income = ledgerRepository.total(TxnType.INCOME, w, year, month);
        BigDecimal expense = ledgerRepository.total(TxnType.EXPENSE, w, year, month);
        return new LedgerPage(income, expense, income.subtract(expense),
                ledgerRepository.search(w, year, month).stream().map(LedgerEntryResponse::from).toList());
    }

    @PreAuthorize(CAN_READ)
    @Transactional(readOnly = true)
    public LedgerEntryResponse get(UUID id) {
        return LedgerEntryResponse.from(find(id));
    }

    @PreAuthorize(CAN_WRITE)
    @Transactional
    public LedgerEntryResponse record(LedgerRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        LedgerEntry t = new LedgerEntry();
        t.setRecordedBy(caller.displayName());
        t.setRecordedById(caller.userId());
        apply(request, t);
        return LedgerEntryResponse.from(ledgerRepository.save(t));
    }

    @PreAuthorize(CAN_WRITE)
    @Transactional
    public LedgerEntryResponse correct(UUID id, LedgerRequest request) {
        LedgerEntry t = find(id);
        Versions.requireCurrent(t.getVersion(), request.version(), "transaction");
        apply(request, t);
        return LedgerEntryResponse.from(ledgerRepository.saveAndFlush(t));
    }

    @PreAuthorize(CAN_WRITE)
    @Transactional
    public void delete(UUID id) {
        ledgerRepository.delete(find(id));
    }

    // ---------------------------------------------------------------------

    private LedgerEntry find(UUID id) {
        return ledgerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Transaction", id));
    }

    private static void apply(LedgerRequest r, LedgerEntry t) {
        t.setWing(normaliseWing(r.wing()));
        t.setCategory(r.category());
        t.setType(r.category().type());
        t.setAmount(r.amount());
        t.setDescription(Text.blankToNull(r.description()));
        t.setTxnDate(r.txnDate());
        t.setReference(Text.blankToNull(r.reference()));
        t.setMonth(r.month() != null ? r.month() : r.txnDate().getMonthValue());
        t.setYear(r.year() != null ? r.year() : r.txnDate().getYear());
    }

    /** "a" → "A", "Common" → "common", blank → no filter. */
    private static String normaliseWing(String wing) {
        String w = Text.blankToNull(wing);
        if (w == null) {
            return null;
        }
        return w.equalsIgnoreCase("common") ? "common" : w.toUpperCase();
    }
}

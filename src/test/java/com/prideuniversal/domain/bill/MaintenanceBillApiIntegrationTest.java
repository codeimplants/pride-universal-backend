package com.prideuniversal.domain.bill;

import com.fasterxml.jackson.databind.JsonNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.settings.WingSettings;
import com.prideuniversal.domain.settings.WingSettingsRepository;
import com.prideuniversal.domain.settings.WingSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Maintenance billing: generation idempotency, payment concurrency, the
 * overdue sweep, and who is allowed to see whose bills.
 *
 * <p>Each test claims its own billing period so that runs cannot collide.
 * Periods are in the past on purpose — generation refuses anything more than a
 * month ahead.
 */
class MaintenanceBillApiIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal MONTHLY_TOTAL = new BigDecimal("2750.00");   // 2000 + 500 + 250
    private static final BigDecimal LATE_FEE = new BigDecimal("100.00");

    /** Distinct past period per test: 2020-01, 2020-02, ... */
    private static final AtomicInteger PERIOD = new AtomicInteger();

    @Autowired
    private MaintenanceBillRepository billRepository;
    @Autowired
    private WingSettingsRepository wingSettingsRepository;
    @Autowired
    private WingSettingsService wingSettingsService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The migration already inserts a wing_settings row per wing with every
     * amount at zero, so this configures the existing rows rather than
     * creating them. Getting that wrong is what made the first run of this
     * suite bill everybody nothing.
     */
    @BeforeEach
    void ensureWingSettings() {
        for (String wing : List.of("A", "B")) {
            WingSettings settings = wingSettingsRepository.findByWing(wing)
                    .orElseGet(() -> {
                        WingSettings created = new WingSettings();
                        created.setWing(wing);
                        return created;
                    });
            if (settings.monthlyTotal().signum() > 0) {
                continue;                       // already configured by an earlier test
            }
            settings.setMonthlyMaintenance(new BigDecimal("2000.00"));
            settings.setSinkingFundAmount(new BigDecimal("500.00"));
            settings.setWaterCharges(new BigDecimal("250.00"));
            settings.setLateFee(LATE_FEE);
            settings.setMaintenanceDueDay(10);
            wingSettingsRepository.save(settings);
            wingSettingsService.evict(wing);    // or the cache serves the zeros
        }
    }

    // =====================================================================
    // Generation
    // =====================================================================

    @Test
    @DisplayName("a resident cannot generate bills")
    void residentCannotGenerateBills() throws Exception {
        String token = loginAsNewResident();
        YearMonth period = nextPeriod();

        mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(period)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("a treasurer generates a bill for every active flat")
    void treasurerGeneratesBills() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();

        mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(period)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isNumber())
                .andExpect(jsonPath("$.skipped").value(0));

        MaintenanceBill bill = billFor(flat, period);
        assertThat(bill.getStatus()).isEqualTo(BillStatus.PENDING);
        assertThat(bill.getAmount()).isEqualByComparingTo(MONTHLY_TOTAL);
        assertThat(bill.getLateFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getDueDate()).isEqualTo(period.atDay(10));
        // Denormalised at generation time, so the bill keeps saying who owed it.
        assertThat(bill.getOwnerName()).isEqualTo(flat.getOwnerName());
    }

    @Test
    @DisplayName("generating twice creates nothing the second time")
    void generateIsIdempotent() throws Exception {
        createFlat();
        String token = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();

        String first = mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(period)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int createdFirst = objectMapper.readTree(first).get("created").asInt();
        assertThat(createdFirst).isPositive();

        // The "generate" button is going to get pressed twice. That must be
        // boring, not an error.
        mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(period)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(createdFirst));
    }

    @Test
    @DisplayName("a period more than a month ahead is refused")
    void futurePeriodIsRejected() throws Exception {
        String token = loginAs(Role.TREASURER);
        YearMonth tooFar = YearMonth.now().plusMonths(6);

        mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(tooFar)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a wing with no settings row at all is refused")
    void wingWithoutSettingsRefusesToBill() {
        assertThatThrownBy(() -> wingSettingsService.ratesFor("ZZ"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no billing settings");
    }

    /**
     * The migration seeds a settings row per wing with every amount at zero,
     * so "the row exists" is not the same as "an amount is configured".
     * Without this guard, generation raises a bill for zero rupees against
     * every flat and reports success.
     */
    @Test
    @DisplayName("a wing whose amounts are all zero is refused, not billed at zero")
    void wingWithZeroAmountsRefusesToBill() {
        jdbcTemplate.update("insert into wing (code, display_name, sort_order) "
                + "values ('C', 'Wing C', 3) on conflict (code) do nothing");
        jdbcTemplate.update("insert into wing_settings (wing) values ('C') "
                + "on conflict (wing) do nothing");

        assertThatThrownBy(() -> wingSettingsService.ratesFor("C"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("maintenance amount of zero");
    }

    // =====================================================================
    // Payment
    // =====================================================================

    @Test
    @DisplayName("recording a payment sets status, receipt and a fresh version")
    void markPaidRecordsPayment() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(token, period);

        MaintenanceBill bill = billFor(flat, period);

        mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("upi", bill.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paid"))
                .andExpect(jsonPath("$.payment_mode").value("upi"))
                .andExpect(jsonPath("$.receipt_number").exists())
                .andExpect(jsonPath("$.version").value(bill.getVersion() + 1));
    }

    @Test
    @DisplayName("marking the same bill paid twice is a conflict, not a silent overwrite")
    void markPaidTwiceIsConflict() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(token, period);

        MaintenanceBill bill = billFor(flat, period);
        String paid = mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("cash", bill.getVersion())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String receipt = objectMapper.readTree(paid).get("receipt_number").asText();
        long currentVersion = objectMapper.readTree(paid).get("version").asLong();

        // Second treasurer, correct version, but the money is already recorded.
        mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("upi", currentVersion)))
                .andExpect(status().isConflict());

        // The original receipt must survive untouched.
        MaintenanceBill after = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(after.getReceiptNumber()).isEqualTo(receipt);
        assertThat(after.getPaymentMode()).isEqualTo(PaymentMode.CASH);
    }

    @Test
    @DisplayName("a stale version loses the race -> 409")
    void markPaidWithStaleVersionIsConflict() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(token, period);

        MaintenanceBill bill = billFor(flat, period);

        mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("cash", bill.getVersion() + 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent_modification"));
    }

    @Test
    @DisplayName("a resident cannot record a payment")
    void residentCannotMarkPaid() throws Exception {
        Flat flat = createFlat();
        String committee = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(committee, period);

        MaintenanceBill bill = billFor(flat, period);
        String resident = loginAs(flat.getOwnerContact());

        mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("cash", bill.getVersion())))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Who sees what
    // =====================================================================

    @Test
    @DisplayName("/mine returns only the caller's own flat's bills")
    void residentSeesOnlyOwnBills() throws Exception {
        Flat mine = createFlat();
        createFlat();                       // somebody else's flat
        String committee = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(committee, period);

        String resident = loginAs(mine.getOwnerContact());
        String body = mockMvc.perform(get("/api/bills/mine")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rows = objectMapper.readTree(body);
        assertThat(rows).isNotEmpty();
        for (JsonNode row : rows) {
            assertThat(row.get("flat_id").asText()).isEqualTo(mine.getId().toString());
        }
    }

    @Test
    @DisplayName("a resident cannot read another flat's bill")
    void residentCannotReadAnotherFlatsBill() throws Exception {
        Flat other = createFlat();
        String committee = loginAs(Role.TREASURER);
        YearMonth period = nextPeriod();
        generate(committee, period);

        MaintenanceBill theirBill = billFor(other, period);
        String resident = loginAsNewResident();

        mockMvc.perform(get("/api/bills/" + theirBill.getId())
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the register is committee-only")
    void registerIsCommitteeOnly() throws Exception {
        String resident = loginAsNewResident();

        mockMvc.perform(get("/api/bills").header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Overdue sweep
    // =====================================================================

    @Test
    @DisplayName("the sweep marks past-due bills overdue and applies the late fee")
    void sweepMarksOverdueAndAppliesLateFee() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.ADMIN);
        YearMonth period = nextPeriod();          // in the past, so already due
        generate(token, period);

        mockMvc.perform(post("/api/bills/run-overdue").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked_overdue").isNumber());

        MaintenanceBill after = billFor(flat, period);
        assertThat(after.getStatus()).isEqualTo(BillStatus.OVERDUE);
        assertThat(after.getLateFee()).isEqualByComparingTo(LATE_FEE);
        assertThat(after.totalDue()).isEqualByComparingTo(MONTHLY_TOTAL.add(LATE_FEE));
    }

    @Test
    @DisplayName("running the sweep twice does not charge the late fee twice")
    void sweepIsIdempotent() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.ADMIN);
        YearMonth period = nextPeriod();
        generate(token, period);

        mockMvc.perform(post("/api/bills/run-overdue").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bills/run-overdue").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // Only PENDING -> OVERDUE is swept, so the second pass sees nothing.
        assertThat(billFor(flat, period).getLateFee()).isEqualByComparingTo(LATE_FEE);
    }

    @Test
    @DisplayName("a paid bill is never marked overdue")
    void paidBillsAreNotSwept() throws Exception {
        Flat flat = createFlat();
        String token = loginAs(Role.ADMIN);
        YearMonth period = nextPeriod();
        generate(token, period);

        MaintenanceBill bill = billFor(flat, period);
        mockMvc.perform(post("/api/bills/" + bill.getId() + "/pay")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson("neft", bill.getVersion())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bills/run-overdue").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        MaintenanceBill after = billFor(flat, period);
        assertThat(after.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(after.getLateFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a resident cannot trigger the sweep")
    void residentCannotRunSweep() throws Exception {
        String resident = loginAsNewResident();

        mockMvc.perform(post("/api/bills/run-overdue").header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static YearMonth nextPeriod() {
        int n = PERIOD.getAndIncrement();
        return YearMonth.of(2020 + n / 12, (n % 12) + 1);
    }

    private void generate(String token, YearMonth period) throws Exception {
        mockMvc.perform(post("/api/bills/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(periodJson(period)))
                .andExpect(status().isOk());
    }

    private MaintenanceBill billFor(Flat flat, YearMonth period) {
        return billRepository
                .findByFlat_IdAndYearAndMonth(flat.getId(), period.getYear(), period.getMonthValue())
                .orElseThrow(() -> new AssertionError(
                        "no bill for flat " + flat.getFlatNumber() + " in " + period));
    }

    private static String periodJson(YearMonth period) {
        return "{\"year\":" + period.getYear() + ",\"month\":" + period.getMonthValue() + "}";
    }

    private static String payJson(String mode, long version) {
        return "{\"payment_mode\":\"" + mode + "\",\"version\":" + version + "}";
    }
}

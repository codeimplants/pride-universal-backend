package com.prideuniversal.domain.settings;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.settings.dto.SettingsDtos.WingSettingsRequest;
import com.prideuniversal.domain.settings.dto.SettingsDtos.WingSettingsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Reading and editing the per-wing settings. Anyone signed in reads them; admin
 * and chairman edit. Every write evicts the billing-rates cache, or the next bill
 * run would use the old amounts.
 */
@Service
public class SettingsService {

    private final WingSettingsRepository repository;
    private final WingSettingsService billingRates;

    public SettingsService(WingSettingsRepository repository, WingSettingsService billingRates) {
        this.repository = repository;
        this.billingRates = billingRates;
    }

    @Transactional(readOnly = true)
    public List<WingSettingsResponse> all() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(WingSettings::getWing))
                .map(WingSettingsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WingSettingsResponse get(String wing) {
        return WingSettingsResponse.from(find(wing));
    }

    @PreAuthorize("hasAnyRole('ADMIN','CHAIRMAN')")
    @Transactional
    public WingSettingsResponse update(String wing, WingSettingsRequest r) {
        WingSettings s = find(wing);
        Versions.requireCurrent(s.getVersion(), r.version(), "wing's settings");

        s.setMonthlyMaintenance(r.monthlyMaintenance());
        s.setMaintenanceDueDay(r.maintenanceDueDay());
        s.setLateFee(orZero(r.lateFee()));
        s.setSinkingFundAmount(orZero(r.sinkingFundAmount()));
        s.setWaterCharges(orZero(r.waterCharges()));
        s.setSocietyName(Text.blankToNull(r.societyName()));
        s.setRegistrationNumber(Text.blankToNull(r.registrationNumber()));
        s.setAddress(Text.blankToNull(r.address()));
        s.setBankName(Text.blankToNull(r.bankName()));
        s.setBankAccount(Text.blankToNull(r.bankAccount()));
        s.setBankIfsc(upper(r.bankIfsc()));
        s.setUpiId(Text.blankToNull(r.upiId()));
        s.setSecretaryName(Text.blankToNull(r.secretaryName()));
        s.setChairmanName(Text.blankToNull(r.chairmanName()));
        s.setTreasurerName(Text.blankToNull(r.treasurerName()));
        s.setTotalFlats(r.totalFlats());

        WingSettingsResponse saved = WingSettingsResponse.from(repository.saveAndFlush(s));
        billingRates.evict(s.getWing());
        return saved;
    }

    private WingSettings find(String wing) {
        String w = wing == null ? "" : wing.trim().toUpperCase();
        return repository.findByWing(w).orElseThrow(() -> ApiException.notFound("Settings for wing", w));
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String upper(String v) {
        String t = Text.blankToNull(v);
        return t == null ? null : t.toUpperCase();
    }
}

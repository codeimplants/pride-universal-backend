package com.prideuniversal.domain.settings;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Per-wing billing configuration and society letterhead details.
 *
 * <p>One row per wing. These are the numbers bill generation multiplies out,
 * so they are read on every generation run and edited perhaps twice a year —
 * which is exactly why {@code wingSettings} is the one thing this application
 * caches.
 */
@Entity
@Table(name = "wing_settings")
@Getter
@Setter
public class WingSettings extends BaseEntity {

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "monthly_maintenance", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyMaintenance = BigDecimal.ZERO;

    /** Day of month a bill falls due. Constrained to 1..28 so every month has one. */
    @Column(name = "maintenance_due_day", nullable = false)
    private int maintenanceDueDay = 10;

    @Column(name = "late_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "sinking_fund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sinkingFundAmount = BigDecimal.ZERO;

    @Column(name = "water_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal waterCharges = BigDecimal.ZERO;

    @Column(name = "society_name", length = 255)
    private String societyName;

    @Column(name = "registration_number", length = 64)
    private String registrationNumber;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "bank_name", length = 160)
    private String bankName;

    @Column(name = "bank_account", length = 32)
    private String bankAccount;

    @Column(name = "bank_ifsc", length = 16)
    private String bankIfsc;

    @Column(name = "upi_id", length = 128)
    private String upiId;

    @Column(name = "secretary_name", length = 160)
    private String secretaryName;

    @Column(name = "chairman_name", length = 160)
    private String chairmanName;

    @Column(name = "treasurer_name", length = 160)
    private String treasurerName;

    @Column(name = "total_flats")
    private Integer totalFlats;

    /**
     * What one flat owes for a month, before any late fee.
     *
     * <p>Kept on the entity rather than in the service because it is a property
     * of the settings, not of the billing run — and it means the sum is stated
     * once instead of at every call site that needs it.
     */
    public BigDecimal monthlyTotal() {
        return monthlyMaintenance
                .add(sinkingFundAmount)
                .add(waterCharges);
    }
}

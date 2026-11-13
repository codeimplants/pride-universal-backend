package com.prideuniversal.domain.settings.dto;

import com.prideuniversal.domain.settings.WingSettings;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class SettingsDtos {

    private SettingsDtos() {}

    private static final String AMOUNT = "Amount cannot be negative";

    public record WingSettingsRequest(
            @NotNull(message = "Monthly maintenance is required") @DecimalMin(value = "0.0", message = AMOUNT)
            @Digits(integer = 10, fraction = 2) BigDecimal monthlyMaintenance,
            @Min(value = 1, message = "Due day is 1 to 28") @Max(value = 28, message = "Due day is 1 to 28")
            int maintenanceDueDay,
            @DecimalMin(value = "0.0", message = AMOUNT) @Digits(integer = 10, fraction = 2) BigDecimal lateFee,
            @DecimalMin(value = "0.0", message = AMOUNT) @Digits(integer = 10, fraction = 2) BigDecimal sinkingFundAmount,
            @DecimalMin(value = "0.0", message = AMOUNT) @Digits(integer = 10, fraction = 2) BigDecimal waterCharges,
            @Size(max = 255) String societyName,
            @Size(max = 64) String registrationNumber,
            @Size(max = 2_000) String address,
            @Size(max = 160) String bankName,
            @Size(max = 32) String bankAccount,
            @Size(max = 16) String bankIfsc,
            @Size(max = 128) String upiId,
            @Size(max = 160) String secretaryName,
            @Size(max = 160) String chairmanName,
            @Size(max = 160) String treasurerName,
            @Min(value = 0) @Max(value = 10_000) Integer totalFlats,
            Long version
    ) {}

    /** Readable by every signed-in user: residents need the bank and UPI details to pay. */
    public record WingSettingsResponse(
            UUID id,
            String wing,
            BigDecimal monthlyMaintenance,
            int maintenanceDueDay,
            BigDecimal lateFee,
            BigDecimal sinkingFundAmount,
            BigDecimal waterCharges,
            /** What a flat is billed each month: maintenance + sinking fund + water. */
            BigDecimal monthlyTotal,
            String societyName,
            String registrationNumber,
            String address,
            String bankName,
            String bankAccount,
            String bankIfsc,
            String upiId,
            String secretaryName,
            String chairmanName,
            String treasurerName,
            Integer totalFlats,
            Long version
    ) {
        public static WingSettingsResponse from(WingSettings s) {
            return new WingSettingsResponse(s.getId(), s.getWing(), s.getMonthlyMaintenance(),
                    s.getMaintenanceDueDay(), s.getLateFee(), s.getSinkingFundAmount(), s.getWaterCharges(),
                    s.monthlyTotal(), s.getSocietyName(), s.getRegistrationNumber(), s.getAddress(), s.getBankName(),
                    s.getBankAccount(), s.getBankIfsc(), s.getUpiId(), s.getSecretaryName(), s.getChairmanName(),
                    s.getTreasurerName(), s.getTotalFlats(), s.getVersion());
        }
    }
}

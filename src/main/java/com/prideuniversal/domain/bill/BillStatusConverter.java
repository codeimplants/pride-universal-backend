package com.prideuniversal.domain.bill;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link BillStatus} as its lowercase code.
 *
 * <p>Same reasoning as {@code RoleConverter}: {@code @Enumerated(STRING)} would
 * write {@code "PENDING"} and the {@code ck_bill_status} CHECK constraint only
 * accepts {@code 'pending'}. The {@code @JsonValue} on the enum governs JSON
 * and tells JPA nothing.
 *
 * <p>This is the third converter of this shape in the codebase. If a fourth
 * appears, extract a shared {@code CodedEnumConverter<E>} base rather than
 * copying it again.
 */
@Converter(autoApply = true)
public class BillStatusConverter implements AttributeConverter<BillStatus, String> {

    @Override
    public String convertToDatabaseColumn(BillStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public BillStatus convertToEntityAttribute(String code) {
        return code == null ? null : BillStatus.fromCode(code);
    }
}

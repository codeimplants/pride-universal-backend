package com.prideuniversal.domain.bill;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link PaymentMode} as its lowercase code. See {@link BillStatusConverter}. */
@Converter(autoApply = true)
public class PaymentModeConverter implements AttributeConverter<PaymentMode, String> {

    @Override
    public String convertToDatabaseColumn(PaymentMode mode) {
        return mode == null ? null : mode.getCode();
    }

    @Override
    public PaymentMode convertToEntityAttribute(String code) {
        return code == null ? null : PaymentMode.fromCode(code);
    }
}

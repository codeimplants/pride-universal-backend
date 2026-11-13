package com.prideuniversal.domain.transaction;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TxnTypeConverter extends CodedEnumConverter<TxnType> {
    public TxnTypeConverter() {
        super(TxnType.class);
    }
}

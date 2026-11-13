package com.prideuniversal.domain.transaction;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TxnCategoryConverter extends CodedEnumConverter<TxnCategory> {
    public TxnCategoryConverter() {
        super(TxnCategory.class);
    }
}

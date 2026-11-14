package com.prideuniversal.domain.business;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BusinessStatusConverter extends CodedEnumConverter<BusinessStatus> {
    public BusinessStatusConverter() {
        super(BusinessStatus.class);
    }
}

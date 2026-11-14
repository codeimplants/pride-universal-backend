package com.prideuniversal.domain.business;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BusinessCategoryConverter extends CodedEnumConverter<BusinessCategory> {
    public BusinessCategoryConverter() {
        super(BusinessCategory.class);
    }
}

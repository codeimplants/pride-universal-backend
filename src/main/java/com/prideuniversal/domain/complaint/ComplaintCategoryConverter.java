package com.prideuniversal.domain.complaint;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ComplaintCategoryConverter extends CodedEnumConverter<ComplaintCategory> {
    public ComplaintCategoryConverter() {
        super(ComplaintCategory.class);
    }
}

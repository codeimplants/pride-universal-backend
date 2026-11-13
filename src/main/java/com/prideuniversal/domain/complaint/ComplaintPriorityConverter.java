package com.prideuniversal.domain.complaint;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ComplaintPriorityConverter extends CodedEnumConverter<ComplaintPriority> {
    public ComplaintPriorityConverter() {
        super(ComplaintPriority.class);
    }
}

package com.prideuniversal.domain.complaint;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ComplaintStatusConverter extends CodedEnumConverter<ComplaintStatus> {
    public ComplaintStatusConverter() {
        super(ComplaintStatus.class);
    }
}

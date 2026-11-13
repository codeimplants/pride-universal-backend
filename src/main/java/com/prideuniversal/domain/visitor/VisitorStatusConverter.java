package com.prideuniversal.domain.visitor;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VisitorStatusConverter extends CodedEnumConverter<VisitorStatus> {
    public VisitorStatusConverter() {
        super(VisitorStatus.class);
    }
}

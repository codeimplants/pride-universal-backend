package com.prideuniversal.domain.visitor;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VisitorPurposeConverter extends CodedEnumConverter<VisitorPurpose> {
    public VisitorPurposeConverter() {
        super(VisitorPurpose.class);
    }
}

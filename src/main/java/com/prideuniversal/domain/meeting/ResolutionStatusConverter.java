package com.prideuniversal.domain.meeting;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResolutionStatusConverter extends CodedEnumConverter<ResolutionStatus> {
    public ResolutionStatusConverter() {
        super(ResolutionStatus.class);
    }
}

package com.prideuniversal.domain.meeting;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MeetingTypeConverter extends CodedEnumConverter<MeetingType> {
    public MeetingTypeConverter() {
        super(MeetingType.class);
    }
}

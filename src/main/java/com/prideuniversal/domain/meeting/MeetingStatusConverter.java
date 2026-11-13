package com.prideuniversal.domain.meeting;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MeetingStatusConverter extends CodedEnumConverter<MeetingStatus> {
    public MeetingStatusConverter() {
        super(MeetingStatus.class);
    }
}

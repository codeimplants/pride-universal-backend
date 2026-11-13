package com.prideuniversal.domain.meeting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Where a meeting is in its lifecycle. Codes match {@code ck_meeting_status} in the schema. */
public enum MeetingStatus implements CodedEnum {

    SCHEDULED("scheduled"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    POSTPONED("postponed");

    private final String code;

    MeetingStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MeetingStatus fromCode(String code) {
        return CodedEnum.fromCode(MeetingStatus.class, code);
    }
}

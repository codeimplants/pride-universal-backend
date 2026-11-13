package com.prideuniversal.domain.meeting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Kind of meeting. Codes match {@code ck_meeting_type} in the schema. */
public enum MeetingType implements CodedEnum {

    AGM("agm"),
    SGM("sgm"),
    COMMITTEE("committee"),
    EMERGENCY("emergency");

    private final String code;

    MeetingType(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MeetingType fromCode(String code) {
        return CodedEnum.fromCode(MeetingType.class, code);
    }
}

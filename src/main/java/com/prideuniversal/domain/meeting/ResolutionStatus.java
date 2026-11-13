package com.prideuniversal.domain.meeting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Outcome of a resolution. Codes match {@code ck_resolution_status} in the schema. */
public enum ResolutionStatus implements CodedEnum {

    PASSED("passed"),
    REJECTED("rejected"),
    DEFERRED("deferred");

    private final String code;

    ResolutionStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ResolutionStatus fromCode(String code) {
        return CodedEnum.fromCode(ResolutionStatus.class, code);
    }
}

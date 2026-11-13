package com.prideuniversal.domain.complaint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Where a complaint is in its lifecycle. Codes match {@code ck_complaint_status} in the schema. */
public enum ComplaintStatus implements CodedEnum {

    OPEN("open"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    CLOSED("closed"),
    REJECTED("rejected");

    private final String code;

    ComplaintStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ComplaintStatus fromCode(String code) {
        return CodedEnum.fromCode(ComplaintStatus.class, code);
    }

    /** Resolved or closed: the schema then requires a resolved date. */
    public boolean isResolved() {
        return this == RESOLVED || this == CLOSED;
    }
}

package com.prideuniversal.domain.visitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** A visit: pending at the gate, then approved or rejected, then checked out. Codes match {@code ck_visitor_status} in the schema. */
public enum VisitorStatus implements CodedEnum {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CHECKED_OUT("checked_out");

    private final String code;

    VisitorStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static VisitorStatus fromCode(String code) {
        return CodedEnum.fromCode(VisitorStatus.class, code);
    }
}

package com.prideuniversal.domain.visitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Why a visitor came. Codes match {@code ck_visitor_purpose} in the schema. */
public enum VisitorPurpose implements CodedEnum {

    PERSONAL("personal"),
    DELIVERY("delivery"),
    SERVICE("service"),
    MAID("maid"),
    GUEST("guest"),
    CAB("cab"),
    OTHER("other");

    private final String code;

    VisitorPurpose(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static VisitorPurpose fromCode(String code) {
        return CodedEnum.fromCode(VisitorPurpose.class, code);
    }
}

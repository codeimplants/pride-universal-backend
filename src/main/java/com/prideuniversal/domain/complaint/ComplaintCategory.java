package com.prideuniversal.domain.complaint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** What a complaint is about. Codes match {@code ck_complaint_category} in the schema. */
public enum ComplaintCategory implements CodedEnum {

    PLUMBING("plumbing"),
    ELECTRICAL("electrical"),
    COMMON_AREA("common_area"),
    SECURITY("security"),
    LIFT("lift"),
    PARKING("parking"),
    NOISE("noise"),
    WATER("water"),
    OTHER("other");

    private final String code;

    ComplaintCategory(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ComplaintCategory fromCode(String code) {
        return CodedEnum.fromCode(ComplaintCategory.class, code);
    }
}

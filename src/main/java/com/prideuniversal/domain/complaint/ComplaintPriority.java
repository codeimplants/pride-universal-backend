package com.prideuniversal.domain.complaint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** How urgent a complaint is. Codes match {@code ck_complaint_priority} in the schema. */
public enum ComplaintPriority implements CodedEnum {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    URGENT("urgent");

    private final String code;

    ComplaintPriority(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ComplaintPriority fromCode(String code) {
        return CodedEnum.fromCode(ComplaintPriority.class, code);
    }
}

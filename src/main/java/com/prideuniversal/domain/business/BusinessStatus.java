package com.prideuniversal.domain.business;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/**
 * A listing's place in review. New listings from members wait as {@code pending}; the committee
 * approves or rejects them, and may later hide one that has gone stale or broken the rules.
 * Codes match {@code ck_biz_status} in the schema.
 */
public enum BusinessStatus implements CodedEnum {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    HIDDEN("hidden");

    private final String code;

    BusinessStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static BusinessStatus fromCode(String code) {
        return CodedEnum.fromCode(BusinessStatus.class, code);
    }
}

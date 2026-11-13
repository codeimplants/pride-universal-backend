package com.prideuniversal.domain.marketplace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Whether a listing is live. Codes match {@code ck_mkt_status} in the schema. */
public enum ListingStatus implements CodedEnum {

    ACTIVE("active"),
    SOLD("sold"),
    REMOVED("removed");

    private final String code;

    ListingStatus(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ListingStatus fromCode(String code) {
        return CodedEnum.fromCode(ListingStatus.class, code);
    }
}

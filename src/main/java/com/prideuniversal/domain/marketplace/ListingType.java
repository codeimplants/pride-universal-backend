package com.prideuniversal.domain.marketplace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Selling, looking to buy, or giving away. Codes match {@code ck_mkt_type} in the schema. */
public enum ListingType implements CodedEnum {

    SELL("sell"),
    BUY("buy"),
    GIVE_AWAY("give_away");

    private final String code;

    ListingType(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ListingType fromCode(String code) {
        return CodedEnum.fromCode(ListingType.class, code);
    }
}

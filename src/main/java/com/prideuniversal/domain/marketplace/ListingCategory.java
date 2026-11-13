package com.prideuniversal.domain.marketplace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** What is being listed. Codes match {@code ck_mkt_category} in the schema. */
public enum ListingCategory implements CodedEnum {

    FURNITURE("furniture"),
    ELECTRONICS("electronics"),
    APPLIANCES("appliances"),
    BOOKS("books"),
    CLOTHES("clothes"),
    VEHICLES("vehicles"),
    KIDS_ITEMS("kids_items"),
    SPORTS("sports"),
    OTHER("other");

    private final String code;

    ListingCategory(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ListingCategory fromCode(String code) {
        return CodedEnum.fromCode(ListingCategory.class, code);
    }
}

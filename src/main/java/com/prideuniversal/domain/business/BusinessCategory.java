package com.prideuniversal.domain.business;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** What a member's business does. Codes match {@code ck_biz_category} in the schema. */
public enum BusinessCategory implements CodedEnum {

    HOME_FOOD("home_food"),
    BAKERY_SWEETS("bakery_sweets"),
    GROCERY_DAIRY("grocery_dairy"),
    CLOTHING_FASHION("clothing_fashion"),
    BEAUTY_WELLNESS("beauty_wellness"),
    HEALTH_MEDICAL("health_medical"),
    TUITION_CLASSES("tuition_classes"),
    FITNESS_YOGA("fitness_yoga"),
    HOME_SERVICES("home_services"),
    ELECTRONICS("electronics"),
    REAL_ESTATE("real_estate"),
    FINANCE_INSURANCE("finance_insurance"),
    LEGAL("legal"),
    TRAVEL("travel"),
    EVENTS_DECOR("events_decor"),
    HANDICRAFTS_GIFTS("handicrafts_gifts"),
    IT_DIGITAL("it_digital"),
    OTHER("other");

    private final String code;

    BusinessCategory(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static BusinessCategory fromCode(String code) {
        return CodedEnum.fromCode(BusinessCategory.class, code);
    }
}

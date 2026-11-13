package com.prideuniversal.domain.vendor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** What a vendor does. Codes match {@code ck_vendor_service} in the schema. */
public enum ServiceType implements CodedEnum {

    PLUMBER("plumber"),
    ELECTRICIAN("electrician"),
    CARPENTER("carpenter"),
    PAINTER("painter"),
    MAID("maid"),
    COOK("cook"),
    DRIVER("driver"),
    PEST_CONTROL("pest_control"),
    AC_REPAIR("ac_repair"),
    APPLIANCE_REPAIR("appliance_repair"),
    SECURITY("security"),
    GARDENER("gardener"),
    OTHER("other");

    private final String code;

    ServiceType(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ServiceType fromCode(String code) {
        return CodedEnum.fromCode(ServiceType.class, code);
    }
}

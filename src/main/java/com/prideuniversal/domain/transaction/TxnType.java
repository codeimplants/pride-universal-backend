package com.prideuniversal.domain.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Money in or money out. Codes match {@code ck_txn_type} in the schema. */
public enum TxnType implements CodedEnum {

    INCOME("income"),
    EXPENSE("expense");

    private final String code;

    TxnType(String code) {
        this.code = code;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TxnType fromCode(String code) {
        return CodedEnum.fromCode(TxnType.class, code);
    }
}

package com.prideuniversal.domain.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.prideuniversal.common.CodedEnum;

/** Ledger category. Each belongs to exactly one {@link TxnType}. Codes match {@code ck_txn_category} in the schema. */
public enum TxnCategory implements CodedEnum {

    MAINTENANCE_COLLECTED("maintenance_collected", TxnType.INCOME),
    SINKING_FUND("sinking_fund", TxnType.INCOME),
    WATER_CHARGES("water_charges", TxnType.INCOME),
    PARKING_CHARGES("parking_charges", TxnType.INCOME),
    INTEREST("interest", TxnType.INCOME),
    DONATION("donation", TxnType.INCOME),
    OTHER_INCOME("other_income", TxnType.INCOME),
    REPAIR_EXPENSE("repair_expense", TxnType.EXPENSE),
    ADMIN_EXPENSE("admin_expense", TxnType.EXPENSE),
    UTILITY_EXPENSE("utility_expense", TxnType.EXPENSE),
    SECURITY_EXPENSE("security_expense", TxnType.EXPENSE),
    CLEANING_EXPENSE("cleaning_expense", TxnType.EXPENSE),
    LIFT_MAINTENANCE("lift_maintenance", TxnType.EXPENSE),
    LEGAL_EXPENSE("legal_expense", TxnType.EXPENSE),
    AUDIT_FEE("audit_fee", TxnType.EXPENSE),
    OTHER_EXPENSE("other_expense", TxnType.EXPENSE);

    private final String code;
    private final TxnType type;

    TxnCategory(String code, TxnType type) {
        this.code = code;
        this.type = type;
    }

    public TxnType type() {
        return type;
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TxnCategory fromCode(String code) {
        return CodedEnum.fromCode(TxnCategory.class, code);
    }
}

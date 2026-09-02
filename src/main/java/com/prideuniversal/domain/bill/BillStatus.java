package com.prideuniversal.domain.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Lifecycle of a maintenance bill.
 *
 * <p>The codes must match {@code ck_bill_status} in the schema exactly:
 * {@code pending, paid, overdue, waived}. Persistence goes through
 * {@link BillStatusConverter} — see {@link com.prideuniversal.common.Role} for
 * why {@code @Enumerated(STRING)} is the wrong tool here.
 */
public enum BillStatus {

    /** Raised, not yet due or not yet paid. */
    PENDING("pending"),

    /** Money received. Requires a paid date and a payment mode. */
    PAID("paid"),

    /** Past its due date and still unpaid. Set by the nightly job. */
    OVERDUE("overdue"),

    /** Written off by the committee. Not collectable, not a debt. */
    WAIVED("waived");

    private final String code;

    BillStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static BillStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown bill status: " + code));
    }

    /** Still owed: neither settled nor written off. */
    public boolean isOutstanding() {
        return this == PENDING || this == OVERDUE;
    }
}

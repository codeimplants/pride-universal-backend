package com.prideuniversal.domain.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * How a maintenance payment was made.
 *
 * <p>Codes must match {@code ck_bill_mode}:
 * {@code cash, upi, cheque, bank_transfer, neft}.
 */
public enum PaymentMode {

    CASH("cash"),
    UPI("upi"),
    CHEQUE("cheque"),
    BANK_TRANSFER("bank_transfer"),
    NEFT("neft");

    private final String code;

    PaymentMode(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PaymentMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(m -> m.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment mode: " + code));
    }
}

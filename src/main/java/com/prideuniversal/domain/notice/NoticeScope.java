package com.prideuniversal.domain.notice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who a notice is addressed to: the whole society, or one wing.
 *
 * <p>The codes must match {@code ck_notice_scope} in the schema exactly.
 */
public enum NoticeScope {

    BOTH("both"),
    WING_A("wing_a"),
    WING_B("wing_b");

    private final String code;

    NoticeScope(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static NoticeScope fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown notice scope: " + code));
    }

    /**
     * The scope addressed to one wing, given a flat's wing code ({@code "A"}).
     *
     * <p>Empty for a wing this enum does not know. That can genuinely happen:
     * the {@code wing} table is data, so a wing C can be added with an INSERT,
     * while this enum and {@code ck_notice_scope} need a code change and a
     * migration. Returning empty rather than throwing means a resident of an
     * unknown wing sees society-wide notices only — the safe direction to fail.
     */
    public static Optional<NoticeScope> forWing(String wingCode) {
        if (wingCode == null) {
            return Optional.empty();
        }
        String wanted = "wing_" + wingCode.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(s -> s.code.equals(wanted))
                .findFirst();
    }
}

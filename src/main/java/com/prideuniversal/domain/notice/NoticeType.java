package com.prideuniversal.domain.notice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * What kind of notice this is.
 *
 * <p>The codes must match {@code ck_notice_type} in the schema exactly.
 * Persistence goes through {@link NoticeTypeConverter}, for the same reason
 * {@code BillStatus} has one — see LEARNING-LOG entry 005.
 */
public enum NoticeType {

    GENERAL("general"),

    /** Annual General Meeting. Statutory for a co-operative housing society. */
    AGM("agm"),

    /** Special General Meeting, called outside the annual cycle. */
    SGM("sgm"),

    MAINTENANCE("maintenance"),
    CIRCULAR("circular"),
    EMERGENCY("emergency"),
    EVENT("event");

    private final String code;

    NoticeType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static NoticeType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown notice type: " + code));
    }

    /**
     * A general meeting notice is a summons: members must be told when and
     * where. One without a date is not a valid notice of a meeting.
     */
    public boolean isMeeting() {
        return this == AGM || this == SGM;
    }
}

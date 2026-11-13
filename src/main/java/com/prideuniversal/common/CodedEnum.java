package com.prideuniversal.common;

import java.util.Arrays;

/**
 * An enum whose wire and database form is a lowercase code ({@code "in_progress"}),
 * not its Java name ({@code IN_PROGRESS}).
 *
 * <p>Three places need the code, and each has its own mechanism:
 * <ul>
 *   <li>JSON — {@code @JsonValue getCode()} / {@code @JsonCreator fromCode()} on each enum;</li>
 *   <li>the database — a {@link CodedEnumConverter} subclass, so the schema's CHECK
 *       constraints (which list the codes) accept the value (LEARNING-LOG entry 005);</li>
 *   <li>query parameters ({@code ?status=open}) — {@code WebConfig}'s converter factory.
 *       Spring's default binds by {@code Enum.valueOf}, which rejects {@code "open"}.</li>
 * </ul>
 */
public interface CodedEnum {

    String getCode();

    static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        String wanted = code.trim();
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.getCode().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown " + type.getSimpleName() + ": " + code));
    }
}

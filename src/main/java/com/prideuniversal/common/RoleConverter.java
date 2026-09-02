package com.prideuniversal.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Role} as its lowercase code ({@code admin}, {@code resident})
 * rather than its Java constant name.
 *
 * <p>Why this exists rather than {@code @Enumerated(EnumType.STRING)}:
 * {@code @Enumerated(STRING)} writes {@link Enum#name()}, which is
 * {@code "ADMIN"} / {@code "COMMITTEE_MEMBER"}. The {@code ck_user_role} CHECK
 * constraint only accepts the lowercase codes, so every insert was rejected
 * with a {@code DataIntegrityViolationException}.
 *
 * <p>The trap: {@code Role} already carries a {@code @JsonValue getCode()}, so
 * the codebase <em>looked</em> like it mapped to lowercase everywhere. But
 * {@code @JsonValue} is a <b>Jackson</b> annotation — it governs JSON
 * serialization only, and JPA has never heard of it. Two independent mapping
 * layers, one shared enum: each needs telling separately. This converter is
 * JPA's half.
 *
 * <p>{@code autoApply = true} means every {@code Role} attribute on every
 * entity uses it without opting in, so the 10 domains still to be built cannot
 * reintroduce the bug by forgetting an annotation.
 *
 * <p>Keeping the database on the same lowercase vocabulary as the JWT claim and
 * the JSON API also means a role read out of a SQL console matches what the
 * client sees — no translation table to remember.
 */
@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role role) {
        return role == null ? null : role.getCode();
    }

    @Override
    public Role convertToEntityAttribute(String code) {
        // fromCode throws on an unknown value rather than returning null: a role
        // we cannot interpret must fail loudly, never silently degrade to one
        // that might grant more access than intended.
        return code == null ? null : Role.fromCode(code);
    }
}

package com.prideuniversal.domain.notice;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link NoticeType} as its lowercase code, so {@code ck_notice_type}
 * accepts it. Same shape as {@code BillStatusConverter}.
 *
 * <p>This and {@link NoticeScopeConverter} make five converters of one shape.
 * {@code BillStatusConverter}'s javadoc said to extract a shared base at the
 * fourth — that refactor is step 5 of the entry 008 exercise.
 */
@Converter(autoApply = true)
public class NoticeTypeConverter implements AttributeConverter<NoticeType, String> {

    @Override
    public String convertToDatabaseColumn(NoticeType type) {
        return type == null ? null : type.getCode();
    }

    @Override
    public NoticeType convertToEntityAttribute(String code) {
        return code == null ? null : NoticeType.fromCode(code);
    }
}

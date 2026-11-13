package com.prideuniversal.domain.notice;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link NoticeType} as its lowercase code, so {@code ck_notice_type}
 * accepts it. Same shape as {@code BillStatusConverter}.
 *
 * <p>One of five converters of this shape; a shared {@code CodedEnumConverter<E>}
 * base would remove the repetition.
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

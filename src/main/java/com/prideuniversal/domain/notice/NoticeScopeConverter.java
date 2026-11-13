package com.prideuniversal.domain.notice;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link NoticeScope} as its lowercase code. See {@link NoticeTypeConverter}. */
@Converter(autoApply = true)
public class NoticeScopeConverter implements AttributeConverter<NoticeScope, String> {

    @Override
    public String convertToDatabaseColumn(NoticeScope scope) {
        return scope == null ? null : scope.getCode();
    }

    @Override
    public NoticeScope convertToEntityAttribute(String code) {
        return code == null ? null : NoticeScope.fromCode(code);
    }
}

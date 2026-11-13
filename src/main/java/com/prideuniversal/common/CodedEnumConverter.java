package com.prideuniversal.common;

import jakarta.persistence.AttributeConverter;

/**
 * Persists a {@link CodedEnum} as its code. Each enum gets a one-line concrete
 * subclass annotated {@code @Converter(autoApply = true)}; Hibernate resolves the
 * enum type from this class's type parameter.
 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum>
        implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected CodedEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.getCode();
    }

    @Override
    public E convertToEntityAttribute(String code) {
        return code == null ? null : CodedEnum.fromCode(type, code);
    }
}

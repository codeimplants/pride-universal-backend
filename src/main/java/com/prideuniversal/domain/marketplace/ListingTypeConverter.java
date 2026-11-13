package com.prideuniversal.domain.marketplace;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ListingTypeConverter extends CodedEnumConverter<ListingType> {
    public ListingTypeConverter() {
        super(ListingType.class);
    }
}

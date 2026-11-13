package com.prideuniversal.domain.marketplace;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ListingCategoryConverter extends CodedEnumConverter<ListingCategory> {
    public ListingCategoryConverter() {
        super(ListingCategory.class);
    }
}

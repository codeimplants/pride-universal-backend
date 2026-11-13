package com.prideuniversal.domain.marketplace;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ListingStatusConverter extends CodedEnumConverter<ListingStatus> {
    public ListingStatusConverter() {
        super(ListingStatus.class);
    }
}

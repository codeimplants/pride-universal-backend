package com.prideuniversal.domain.vendor;

import com.prideuniversal.common.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ServiceTypeConverter extends CodedEnumConverter<ServiceType> {
    public ServiceTypeConverter() {
        super(ServiceType.class);
    }
}

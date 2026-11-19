package com.prideuniversal.domain.community;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;

/**
 * A wall-clock time stored as minutes since midnight. Kept out of SQL TIME because Hibernate shifts
 * TIME values between the JVM zone and {@code jdbc.time_zone}, which wraps 22:00 round to 03:30.
 */
@Converter
public class MinuteOfDay implements AttributeConverter<LocalTime, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : time.getHour() * 60 + time.getMinute();
    }

    @Override
    public LocalTime convertToEntityAttribute(Integer minutes) {
        return minutes == null ? null : LocalTime.of(minutes / 60 % 24, minutes % 60);
    }
}

package ru.sportsresults.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Duration;

@Converter(autoApply = true)
public class DurationMillisConverter implements AttributeConverter<Duration, Long> {

    @Override
    public Long convertToDatabaseColumn(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    @Override
    public Duration convertToEntityAttribute(Long milliseconds) {
        return milliseconds == null ? null : Duration.ofMillis(milliseconds);
    }
}

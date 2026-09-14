package ru.sportsresults.service;

import ru.sportsresults.domain.ResultInquiryAvailability;

import java.time.Instant;

public record ResultInquiryAvailabilityDecision(
        ResultInquiryAvailability state,
        Instant deadline
) {
}

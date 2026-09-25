package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.EventPublicationStatus;

import java.time.Instant;

public record CreateEventRequest(
        @NotNull Long eventSeriesId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 160)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL slug")
        String slug,
        Instant startsAt,
        Instant endsAt,
        @Size(max = 255) String location,
        @Size(max = 64) String timeZone,
        EventPublicationStatus publicationStatus,
        @Valid UpdateResultInquirySettingsRequest resultInquiry
) {
    public CreateEventRequest(
            Long eventSeriesId,
            String name,
            String slug,
            Instant startsAt,
            Instant endsAt,
            String location,
            String timeZone,
            EventPublicationStatus publicationStatus
    ) {
        this(eventSeriesId, name, slug, startsAt, endsAt, location, timeZone, publicationStatus, null);
    }
}

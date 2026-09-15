package ru.sportsresults.api.dto;

import ru.sportsresults.domain.EventPhase;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.time.Instant;
import java.util.List;

public record EventDetailsDto(
        Long id,
        Long eventSeriesId,
        String eventSeriesName,
        String eventSeriesSlug,
        String name,
        String slug,
        Instant startsAt,
        Instant endsAt,
        String location,
        String timeZone,
        EventPhase phase,
        EventPublicationStatus publicationStatus,
        ResultsPublicationStatus resultsPublicationStatus,
        boolean resultsPublished,
        EventParticipantInfoDto participantInfo,
        List<EventScheduleItemDto> schedule,
        List<EventInfoBlockDto> infoBlocks,
        List<EventDocumentDto> documents,
        List<PublicRaceDto> races
) {
    public EventDetailsDto {
        schedule = List.copyOf(schedule);
        infoBlocks = List.copyOf(infoBlocks);
        documents = List.copyOf(documents);
        races = List.copyOf(races);
    }
}

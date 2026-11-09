package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.JournalShareDtos;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.JournalSharePermission;
import ru.sportsresults.domain.ResultIssueJournalShareGrant;
import ru.sportsresults.domain.ResultIssueJournalShareSession;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.ResultIssueJournalShareGrantRepository;
import ru.sportsresults.repository.ResultIssueJournalShareSessionRepository;
import ru.sportsresults.repository.ResultIssueWorkspaceQueryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class JournalShareAccessService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final ResultIssueJournalShareGrantRepository grantRepository;
    private final ResultIssueJournalShareSessionRepository sessionRepository;
    private final EventSeriesRepository seriesRepository;
    private final ResultIssueWorkspaceQueryRepository workspaceRepository;
    private final ResultIssueShareTokenService tokenService;

    public JournalShareAccessService(
            ResultIssueJournalShareGrantRepository grantRepository,
            ResultIssueJournalShareSessionRepository sessionRepository,
            EventSeriesRepository seriesRepository,
            ResultIssueWorkspaceQueryRepository workspaceRepository,
            ResultIssueShareTokenService tokenService
    ) {
        this.grantRepository = grantRepository;
        this.sessionRepository = sessionRepository;
        this.seriesRepository = seriesRepository;
        this.workspaceRepository = workspaceRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public JournalShareDtos.Created create(JournalShareDtos.CreateRequest request, String actor) {
        EventSeries series = seriesRepository.findById(request.eventSeriesId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVENT_SERIES_NOT_FOUND", "Event series not found"));
        if (workspaceRepository.findSheet(series.getId(), request.year(), null) == null) {
            throw new InvalidRequestException("JOURNAL_SHARE_SHEET_NOT_FOUND", "Journal sheet not found");
        }
        String cityScope = canonicalCity(series.getId(), request.year(), request.cityScope());
        if (request.canSyncNow() && request.permission() != JournalSharePermission.EDIT) {
            throw new InvalidRequestException(
                    "JOURNAL_SHARE_INVALID_PERMISSION", "Synchronization requires EDIT permission");
        }

        GeneratedShareToken token = tokenService.generate();
        Instant now = Instant.now();
        ResultIssueJournalShareGrant grant = new ResultIssueJournalShareGrant();
        grant.setId(UUID.randomUUID());
        grant.setTokenHash(token.tokenHash());
        grant.setEventSeries(series);
        grant.setSheetYear(request.year());
        grant.setCityScope(cityScope);
        grant.setPermission(request.permission());
        grant.setCanSyncNow(request.canSyncNow());
        grant.setLabel(normalize(request.label(), 160));
        grant.setCreatedAt(now);
        grant.setCreatedBy(actor);
        grant.setExpiresAt(request.noExpiry() ? null : now.plus(Duration.ofDays(request.lifetimeDays())));
        grant = grantRepository.saveAndFlush(grant);
        return new JournalShareDtos.Created(toDto(grant), "/journal-share/" + token.rawToken());
    }

    @Transactional(readOnly = true)
    public List<JournalShareDtos.Grant> list() {
        return grantRepository.findAllByOrderByCreatedAtDesc().stream().map(JournalShareAccessService::toDto).toList();
    }

    @Transactional
    public JournalShareDtos.Grant revoke(UUID id, String actor) {
        ResultIssueJournalShareGrant grant = grantRepository.findByIdForUpdate(id)
                .orElseThrow(JournalShareAccessService::notFound);
        if (grant.getRevokedAt() == null) {
            grant.setRevokedAt(Instant.now());
            grant.setRevokedBy(actor);
            grant = grantRepository.saveAndFlush(grant);
        }
        return toDto(grant);
    }

    @Transactional
    public JournalShareDtos.ExchangeResponse exchange(String rawToken) {
        if (!validTokenShape(rawToken)) {
            throw notFound();
        }
        ResultIssueJournalShareGrant grant = grantRepository
                .findByTokenHashForUpdate(tokenService.hash(rawToken))
                .orElseThrow(JournalShareAccessService::notFound);
        Instant now = Instant.now();
        requireActive(grant, now);

        GeneratedShareToken token = tokenService.generate();
        Instant sessionExpiry = now.plus(SESSION_TTL);
        if (grant.getExpiresAt() != null && grant.getExpiresAt().isBefore(sessionExpiry)) {
            sessionExpiry = grant.getExpiresAt();
        }
        ResultIssueJournalShareSession session = new ResultIssueJournalShareSession();
        session.setId(UUID.randomUUID());
        session.setGrant(grant);
        session.setSessionTokenHash(token.tokenHash());
        session.setCreatedAt(now);
        session.setExpiresAt(sessionExpiry);
        session.setLastSeenAt(now);
        sessionRepository.saveAndFlush(session);

        grant.setLastAccessedAt(now);
        grant.setAccessCount(grant.getAccessCount() + 1);
        grantRepository.save(grant);
        return new JournalShareDtos.ExchangeResponse(token.rawToken(), sessionExpiry, context(grant));
    }

    @Transactional
    public AccessContext authenticate(String rawSessionToken) {
        if (!validTokenShape(rawSessionToken)) {
            throw notFound();
        }
        ResultIssueJournalShareSession session = sessionRepository
                .findBySessionTokenHash(tokenService.hash(rawSessionToken))
                .orElseThrow(JournalShareAccessService::notFound);
        Instant now = Instant.now();
        if (!session.getExpiresAt().isAfter(now)) {
            throw notFound();
        }
        ResultIssueJournalShareGrant grant = session.getGrant();
        requireActive(grant, now);
        session.setLastSeenAt(now);
        sessionRepository.save(session);
        return new AccessContext(grant);
    }

    private String canonicalCity(long seriesId, int year, String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String normalized = ResultIssueWorkspaceQueryRepository.normalizeCity(requested);
        return workspaceRepository.findCities(seriesId, year).stream()
                .filter(city -> Objects.equals(
                        ResultIssueWorkspaceQueryRepository.normalizeCity(city), normalized))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "JOURNAL_SHARE_CITY_NOT_FOUND", "City is not part of this journal sheet"));
    }

    private static void requireActive(ResultIssueJournalShareGrant grant, Instant now) {
        if (grant.getRevokedAt() != null
                || (grant.getExpiresAt() != null && !grant.getExpiresAt().isAfter(now))) {
            throw notFound();
        }
    }

    private static JournalShareDtos.Context context(ResultIssueJournalShareGrant grant) {
        return new JournalShareDtos.Context(
                grant.getId(), grant.getLabel(), grant.getEventSeries().getId(),
                grant.getEventSeries().getName(), grant.getSheetYear(), grant.getCityScope(),
                grant.getPermission(), grant.isCanSyncNow(), grant.getExpiresAt()
        );
    }

    private static JournalShareDtos.Grant toDto(ResultIssueJournalShareGrant grant) {
        return new JournalShareDtos.Grant(
                grant.getId(), grant.getEventSeries().getId(), grant.getEventSeries().getName(),
                grant.getSheetYear(), grant.getCityScope(), grant.getPermission(), grant.isCanSyncNow(),
                grant.getLabel(), grant.getExpiresAt(), grant.getCreatedAt(), grant.getCreatedBy(),
                grant.getRevokedAt(), grant.getRevokedBy(), grant.getLastAccessedAt(), grant.getAccessCount()
        );
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidRequestException("JOURNAL_SHARE_LABEL_INVALID", "Share label is too long");
        }
        return normalized;
    }

    private static boolean validTokenShape(String token) {
        return token != null && token.length() >= 40 && token.length() <= 128
                && token.chars().allMatch(character -> Character.isLetterOrDigit(character)
                || character == '-' || character == '_');
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("JOURNAL_SHARE_NOT_FOUND", "Journal share access is unavailable");
    }

    public record AccessContext(ResultIssueJournalShareGrant grant) {
        public long seriesId() { return grant.getEventSeries().getId(); }
        public int year() { return grant.getSheetYear(); }
        public String cityScope() { return grant.getCityScope(); }
        public boolean canEdit() { return grant.getPermission() == JournalSharePermission.EDIT; }
        public boolean canSyncNow() { return canEdit() && grant.isCanSyncNow(); }
        public String actor() { return "SHARE:" + grant.getId(); }
        public JournalShareDtos.Context dto() { return context(grant); }
    }
}

package ru.sportsresults.service;

import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CertificateAvailabilityDto;
import ru.sportsresults.api.dto.CertificateSettingsDto;
import ru.sportsresults.api.dto.CertificateTemplateDto;
import ru.sportsresults.api.dto.CertificateTemplateLayoutDto;
import ru.sportsresults.api.dto.UpdateCertificateSettingsRequest;
import ru.sportsresults.api.dto.UpdateCertificateTemplateRequest;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.*;
import ru.sportsresults.storage.FileStorageService;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CertificateService {
    private static final String PDF_TYPE = "application/pdf";

    private final EventService eventService;
    private final CertificateSettingsRepository settingsRepository;
    private final CertificateTemplateRepository templateRepository;
    private final RaceRepository raceRepository;
    private final ResultRepository resultRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final OfficialRankingService rankingService;
    private final PublicCategoryPresentation categoryPresentation;
    private final FileStorageService storage;
    private final CertificateTemplateCache cache;
    private final CertificateGenerationGuard guard;
    private final CertificatePdfRenderer renderer;
    private final AdminChangeLogRepository auditRepository;
    private final long maxTemplateBytes;
    private final long maxGeneratedBytes;

    public CertificateService(
            EventService eventService,
            CertificateSettingsRepository settingsRepository,
            CertificateTemplateRepository templateRepository,
            RaceRepository raceRepository,
            ResultRepository resultRepository,
            AwardPolicyRepository awardPolicyRepository,
            OfficialRankingService rankingService,
            PublicCategoryPresentation categoryPresentation,
            FileStorageService storage,
            CertificateTemplateCache cache,
            CertificateGenerationGuard guard,
            CertificatePdfRenderer renderer,
            AdminChangeLogRepository auditRepository,
            @Value("${app.certificates.max-template-size-bytes:10485760}") long maxTemplateBytes,
            @Value("${app.certificates.max-generated-size-bytes:15728640}") long maxGeneratedBytes
    ) {
        this.eventService = eventService;
        this.settingsRepository = settingsRepository;
        this.templateRepository = templateRepository;
        this.raceRepository = raceRepository;
        this.resultRepository = resultRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.rankingService = rankingService;
        this.categoryPresentation = categoryPresentation;
        this.storage = storage;
        this.cache = cache;
        this.guard = guard;
        this.renderer = renderer;
        this.auditRepository = auditRepository;
        this.maxTemplateBytes = maxTemplateBytes;
        this.maxGeneratedBytes = maxGeneratedBytes;
    }

    @Transactional(readOnly = true)
    public CertificateSettingsDto adminSettings(Long eventId) {
        eventService.requireEvent(eventId);
        return settingsRepository.findByEventId(eventId)
                .map(CertificateService::settingsDto)
                .orElseGet(() -> new CertificateSettingsDto(false, Set.of()));
    }

    @Transactional
    public CertificateSettingsDto updateSettings(Long eventId, UpdateCertificateSettingsRequest request, String actor) {
        Event event = eventService.requireEvent(eventId);
        CertificateSettings settings = settingsRepository.findByEventId(eventId).orElseGet(() -> {
            CertificateSettings created = new CertificateSettings();
            created.setEvent(event);
            return created;
        });
        String oldValue = settingsSummary(settings);
        settings.setEnabled(request.enabled());
        settings.setAllowedFields(request.allowedFields());
        CertificateSettings saved = settingsRepository.save(settings);
        audit(actor, AuditEntityType.CERTIFICATE_SETTINGS, saved.getId(), "settings", oldValue, settingsSummary(saved));
        return settingsDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CertificateTemplateDto> listTemplates(Long eventId) {
        eventService.requireEvent(eventId);
        return templateRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(CertificateService::templateDto).toList();
    }

    @Transactional
    public CertificateTemplateDto upload(Long eventId, String displayName, int displayOrder, boolean active,
                                         Set<Long> raceIds, String filename, String contentType, byte[] bytes,
                                         String actor) {
        Event event = eventService.requireEvent(eventId);
        validateName(displayName);
        validatePdf(filename, contentType, bytes);
        Set<Race> races = ownedRaces(eventId, raceIds);
        FileStorageService.StoredDocument stored = storage.storeCertificateTemplate(eventId, bytes);
        try {
            CertificateTemplate template = new CertificateTemplate();
            template.setEvent(event);
            template.setDisplayName(displayName.strip());
            template.setDisplayOrder(displayOrder);
            template.setActive(active);
            template.setRaces(races);
            template.setOriginalFilename(safeFilename(filename));
            template.setStorageProvider(stored.provider());
            template.setStorageKey(stored.storageKey());
            template.setContentType(PDF_TYPE);
            template.setSizeBytes(bytes.length);
            template.setSha256(sha256(bytes));
            template.setUploadedAt(Instant.now());
            template.setUploadedBy(actor);
            CertificateTemplate saved = templateRepository.saveAndFlush(template);
            audit(actor, AuditEntityType.CERTIFICATE_TEMPLATE, saved.getId(), "uploaded", null, templateSummary(saved));
            return templateDto(saved);
        } catch (RuntimeException exception) {
            storage.delete(stored.provider(), stored.storageKey());
            throw exception;
        }
    }

    @Transactional
    public CertificateTemplateDto update(Long eventId, Long templateId, UpdateCertificateTemplateRequest request,
                                         String actor) {
        CertificateTemplate template = ownedTemplate(eventId, templateId);
        validateName(request.displayName());
        validateLayout(request.layout());
        String oldValue = templateSummary(template);
        template.setDisplayName(request.displayName().strip());
        template.setActive(request.active());
        template.setDisplayOrder(request.displayOrder());
        template.setRaces(ownedRaces(eventId, request.raceIds()));
        applyLayout(template, request.layout());
        CertificateTemplate saved = templateRepository.save(template);
        audit(actor, AuditEntityType.CERTIFICATE_TEMPLATE, saved.getId(), "updated", oldValue, templateSummary(saved));
        return templateDto(saved);
    }

    @Transactional
    public void delete(Long eventId, Long templateId, String actor) {
        CertificateTemplate template = ownedTemplate(eventId, templateId);
        String oldValue = templateSummary(template);
        EventDocumentStorageProvider provider = template.getStorageProvider();
        String key = template.getStorageKey();
        templateRepository.delete(template);
        templateRepository.flush();
        storage.delete(provider, key);
        cache.invalidate(templateId);
        audit(actor, AuditEntityType.CERTIFICATE_TEMPLATE, templateId, "deleted", oldValue, null);
    }

    @Transactional(readOnly = true)
    public CertificateAvailabilityDto availability(Long resultId) {
        Result result = publicFinishedResult(resultId, false);
        if (result == null) return new CertificateAvailabilityDto(false, List.of(), List.of());
        Long eventId = result.getRegistration().getRace().getEvent().getId();
        CertificateSettings settings = settingsRepository.findByEventId(eventId).orElse(null);
        if (settings == null || !settings.isEnabled()) return new CertificateAvailabilityDto(false, List.of(), List.of());
        List<CertificateAvailabilityDto.TemplateOption> templates = applicableTemplates(eventId,
                result.getRegistration().getRace().getId()).stream()
                .map(item -> new CertificateAvailabilityDto.TemplateOption(item.getId(), item.getDisplayName())).toList();
        Map<CertificateField, String> values = certificateValues(result);
        List<CertificateAvailabilityDto.FieldOption> fields = settings.getAllowedFields().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(field -> new CertificateAvailabilityDto.FieldOption(field, label(field), values.get(field) != null))
                .toList();
        return new CertificateAvailabilityDto(!templates.isEmpty(), templates, fields);
    }

    @Transactional(readOnly = true)
    public GeneratedCertificate generate(Long resultId, Long templateId, Set<CertificateField> requestedFields) {
        Result result = publicFinishedResult(resultId, true);
        Race race = result.getRegistration().getRace();
        Long eventId = race.getEvent().getId();
        CertificateSettings settings = settingsRepository.findByEventId(eventId)
                .filter(CertificateSettings::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("CERTIFICATES_NOT_AVAILABLE", "Certificates are not available"));
        CertificateTemplate template = ownedTemplate(eventId, templateId);
        if (!template.isActive() || (!template.getRaces().isEmpty() && template.getRaces().stream().noneMatch(item -> item.getId().equals(race.getId())))) {
            throw new ResourceNotFoundException("CERTIFICATE_TEMPLATE_NOT_FOUND", "Certificate template not found");
        }
        Set<CertificateField> requested = requestedFields == null ? Set.of() : Set.copyOf(requestedFields);
        if (!settings.getAllowedFields().containsAll(requested)) {
            throw new InvalidRequestException("CERTIFICATE_FIELD_NOT_ALLOWED", "One or more certificate fields are not allowed");
        }
        Map<CertificateField, String> values = certificateValues(result);
        if (requested.stream().anyMatch(field -> values.get(field) == null)) {
            throw new InvalidRequestException("CERTIFICATE_FIELD_UNAVAILABLE", "One or more certificate fields are unavailable");
        }
        byte[] source = templateBytes(template);
        List<String> dynamic = requested.stream().sorted(Comparator.comparingInt(Enum::ordinal))
                .map(field -> label(field) + ": " + values.get(field)).toList();
        Event event = race.getEvent();
        CertificatePdfRenderer.CertificateContent content = new CertificatePdfRenderer.CertificateContent(
                result.getRegistration().getDisplayName(),
                List.of(event.getName(), nonBlank(event.getLocation(), "Место не указано"), race.getName()),
                dynamic
        );
        byte[] generated = guard.execute(() -> renderer.render(source, layoutDto(template), content));
        if (generated.length > maxGeneratedBytes) {
            throw new InvalidRequestException("CERTIFICATE_TOO_LARGE", "Generated certificate exceeds the configured maximum size");
        }
        return new GeneratedCertificate(generated, safeDownloadFilename(result));
    }

    @Transactional(readOnly = true)
    public GeneratedCertificate preview(Long eventId, Long templateId, Set<CertificateField> fields) {
        eventService.requireEvent(eventId);
        CertificateTemplate template = ownedTemplate(eventId, templateId);
        Set<CertificateField> selected = fields == null || fields.isEmpty()
                ? EnumSet.of(CertificateField.OFFICIAL_TIME, CertificateField.OFFICIAL_PLACE)
                : EnumSet.copyOf(fields);
        Map<CertificateField, String> sample = syntheticValues();
        List<String> dynamic = selected.stream().sorted(Comparator.comparingInt(Enum::ordinal))
                .map(field -> label(field) + ": " + sample.get(field)).toList();
        byte[] generated = guard.execute(() -> renderer.render(templateBytes(template), layoutDto(template),
                new CertificatePdfRenderer.CertificateContent("Иван Петров",
                        List.of("Тестовый забег", "Москва", "10 км"), dynamic)));
        return new GeneratedCertificate(generated, "certificate-preview.pdf");
    }

    private Result publicFinishedResult(Long resultId, boolean fail) {
        Result result = resultRepository.findById(resultId).orElse(null);
        boolean available = result != null && PublicResultVisibility.isResultPublic(result)
                && ResultStatus.FINISHED.code().equalsIgnoreCase(result.getStatus());
        if (!available && fail) throw new ResourceNotFoundException("RESULT_NOT_FOUND", "Published finished result not found");
        return available ? result : null;
    }

    private List<CertificateTemplate> applicableTemplates(Long eventId, Long raceId) {
        return templateRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .filter(CertificateTemplate::isActive)
                .filter(template -> template.getRaces().isEmpty()
                        || template.getRaces().stream().anyMatch(race -> race.getId().equals(raceId)))
                .toList();
    }

    Map<CertificateField, String> certificateValues(Result result) {
        Registration registration = result.getRegistration();
        Race race = registration.getRace();
        RankingBasis basis = awardPolicyRepository.findByRaceId(race.getId())
                .map(AwardPolicy::getRankingBasis).orElse(race.getPublicRankingBasis());
        List<RankingAchievementDto> achievements = rankingService.findForResult(
                race.getEvent().getId(), race.getId(), result.getId());
        Integer officialPlace = achievements.stream()
                .filter(item -> item.type() == RankingAchievementType.ABSOLUTE || item.type() == RankingAchievementType.GENDER)
                .map(RankingAchievementDto::place).findFirst().orElse(null);
        EnumMap<CertificateField, String> values = new EnumMap<>(CertificateField.class);
        values.put(CertificateField.GUN_TIME, duration(result.getGunTime()));
        values.put(CertificateField.CHIP_TIME, duration(result.getChipTime()));
        values.put(CertificateField.OFFICIAL_TIME, switch (basis) {
            case GUN_TIME -> duration(result.getGunTime());
            case CHIP_TIME -> duration(result.getChipTime());
            case NONE -> null;
        });
        values.put(CertificateField.OFFICIAL_PLACE, place(officialPlace));
        values.put(CertificateField.OVERALL_PLACE, place(result.getOverallPlace()));
        values.put(CertificateField.GENDER_PLACE, place(result.getGenderPlace()));
        values.put(CertificateField.CATEGORY_PLACE, place(result.getCategoryPlace()));
        values.put(CertificateField.CATEGORY_NAME, registration.getCategory() == null ? null
                : categoryPresentation.publicName(registration.getCategory()));
        values.put(CertificateField.BIB, blankToNull(registration.getBib()));
        return values;
    }

    private byte[] templateBytes(CertificateTemplate template) {
        return cache.get(template.getId(), template.getSha256(), () -> {
            try (InputStream input = storage.open(template.getStorageProvider(), template.getStorageKey())) {
                byte[] bytes = input.readNBytes(Math.toIntExact(maxTemplateBytes + 1));
                if (bytes.length == 0 || bytes.length > maxTemplateBytes) {
                    throw new InvalidRequestException("INVALID_CERTIFICATE_TEMPLATE", "Certificate template is empty or too large");
                }
                return bytes;
            } catch (InvalidRequestException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot read certificate template", exception);
            }
        });
    }

    private Set<Race> ownedRaces(Long eventId, Set<Long> raceIds) {
        if (raceIds == null || raceIds.isEmpty()) return Set.of();
        List<Race> races = raceRepository.findAllById(raceIds);
        if (races.size() != raceIds.size() || races.stream().anyMatch(race -> !race.getEvent().getId().equals(eventId))) {
            throw new InvalidRequestException("INVALID_CERTIFICATE_RACES", "All selected races must belong to the event");
        }
        return new LinkedHashSet<>(races);
    }

    private CertificateTemplate ownedTemplate(Long eventId, Long templateId) {
        return templateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("CERTIFICATE_TEMPLATE_NOT_FOUND", "Certificate template not found"));
    }

    private void validatePdf(String filename, String contentType, byte[] bytes) {
        if (filename == null || filename.isBlank()) throw new InvalidRequestException("INVALID_CERTIFICATE_FILENAME", "Filename is required");
        if (bytes == null || bytes.length < 5 || bytes.length > maxTemplateBytes) {
            throw new InvalidRequestException("INVALID_CERTIFICATE_SIZE", "PDF must be non-empty and no larger than configured maximum");
        }
        if (!PDF_TYPE.equalsIgnoreCase(contentType) || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new InvalidRequestException("INVALID_CERTIFICATE_TYPE", "Only valid PDF templates are accepted");
        }
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() < 1) throw new IllegalArgumentException("no pages");
        } catch (Exception exception) {
            throw new InvalidRequestException("INVALID_CERTIFICATE_PDF", "PDF template is corrupt or unsupported");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new InvalidRequestException("INVALID_CERTIFICATE_NAME", "Certificate template name is required");
        }
    }

    private static void validateLayout(CertificateTemplateLayoutDto layout) {
        List<BigDecimal> values = List.of(layout.participantWidth(), layout.participantFontSize(), layout.participantMinFontSize(),
                layout.eventWidth(), layout.eventFontSize(), layout.eventMinFontSize(), layout.eventLineSpacing(),
                layout.dynamicWidth(), layout.dynamicMaxHeight(), layout.dynamicFontSize(), layout.dynamicMinFontSize(),
                layout.dynamicLineSpacing());
        if (values.stream().anyMatch(value -> value.signum() <= 0 || value.compareTo(new BigDecimal("5000")) > 0)
                || layout.participantMinFontSize().compareTo(layout.participantFontSize()) > 0
                || layout.eventMinFontSize().compareTo(layout.eventFontSize()) > 0
                || layout.dynamicMinFontSize().compareTo(layout.dynamicFontSize()) > 0) {
            throw new InvalidRequestException("INVALID_CERTIFICATE_LAYOUT", "Certificate layout values are invalid");
        }
    }

    private static CertificateSettingsDto settingsDto(CertificateSettings value) {
        return new CertificateSettingsDto(value.isEnabled(), value.getAllowedFields());
    }

    private static CertificateTemplateDto templateDto(CertificateTemplate value) {
        return new CertificateTemplateDto(value.getId(), value.getDisplayName(), value.isActive(), value.getDisplayOrder(),
                value.getOriginalFilename(), value.getContentType(), value.getSizeBytes(), value.getSha256(),
                value.getUploadedAt(), value.getUploadedBy(), value.getRaces().stream().map(Race::getId).collect(Collectors.toCollection(LinkedHashSet::new)),
                layoutDto(value));
    }

    private static CertificateTemplateLayoutDto layoutDto(CertificateTemplate value) {
        return new CertificateTemplateLayoutDto(value.getParticipantX(), value.getParticipantY(), value.getParticipantWidth(),
                value.getParticipantFontSize(), value.getParticipantMinFontSize(), value.getParticipantAlignment(),
                value.getEventX(), value.getEventY(), value.getEventWidth(), value.getEventFontSize(), value.getEventMinFontSize(),
                value.getEventLineSpacing(), value.getEventAlignment(), value.getDynamicX(), value.getDynamicY(),
                value.getDynamicWidth(), value.getDynamicMaxHeight(), value.getDynamicFontSize(), value.getDynamicMinFontSize(),
                value.getDynamicLineSpacing(), value.getDynamicAlignment());
    }

    private static void applyLayout(CertificateTemplate value, CertificateTemplateLayoutDto layout) {
        value.setParticipantX(layout.participantX()); value.setParticipantY(layout.participantY());
        value.setParticipantWidth(layout.participantWidth()); value.setParticipantFontSize(layout.participantFontSize());
        value.setParticipantMinFontSize(layout.participantMinFontSize()); value.setParticipantAlignment(layout.participantAlignment());
        value.setEventX(layout.eventX()); value.setEventY(layout.eventY()); value.setEventWidth(layout.eventWidth());
        value.setEventFontSize(layout.eventFontSize()); value.setEventMinFontSize(layout.eventMinFontSize());
        value.setEventLineSpacing(layout.eventLineSpacing()); value.setEventAlignment(layout.eventAlignment());
        value.setDynamicX(layout.dynamicX()); value.setDynamicY(layout.dynamicY()); value.setDynamicWidth(layout.dynamicWidth());
        value.setDynamicMaxHeight(layout.dynamicMaxHeight()); value.setDynamicFontSize(layout.dynamicFontSize());
        value.setDynamicMinFontSize(layout.dynamicMinFontSize()); value.setDynamicLineSpacing(layout.dynamicLineSpacing());
        value.setDynamicAlignment(layout.dynamicAlignment());
    }

    private static String label(CertificateField field) {
        return switch (field) {
            case OFFICIAL_TIME -> "Официальное время";
            case GUN_TIME -> "Время от выстрела";
            case CHIP_TIME -> "Чистое время";
            case OFFICIAL_PLACE -> "Официальное место";
            case OVERALL_PLACE -> "Абсолютное место";
            case GENDER_PLACE -> "Место среди мужчин / женщин";
            case CATEGORY_PLACE -> "Место в категории";
            case CATEGORY_NAME -> "Категория";
            case BIB -> "Стартовый номер";
        };
    }

    private static String duration(Duration value) {
        if (value == null) return null;
        long seconds = value.toSeconds();
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    private static String place(Integer value) { return value == null ? null : value + " место"; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String safeFilename(String value) {
        String safe = value.replace('\\', '/'); safe = safe.substring(safe.lastIndexOf('/') + 1).strip();
        if (safe.isBlank() || safe.length() > 255) throw new InvalidRequestException("INVALID_CERTIFICATE_FILENAME", "Invalid filename");
        return safe;
    }
    private static String safeDownloadFilename(Result result) {
        return "certificate-result-" + result.getId() + "-" + result.getRegistration().getRace().getSlug().replaceAll("[^a-zA-Z0-9_-]", "-") + ".pdf";
    }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String settingsSummary(CertificateSettings value) {
        return value.isEnabled() + ":" + value.getAllowedFields().stream().sorted().toList();
    }
    private static String templateSummary(CertificateTemplate value) {
        return value.getDisplayName() + ":" + value.isActive() + ":" + value.getDisplayOrder() + ":"
                + value.getRaces().stream().map(Race::getId).sorted().toList() + ":" + value.getSha256();
    }
    private static Map<CertificateField, String> syntheticValues() {
        return Map.ofEntries(
                Map.entry(CertificateField.OFFICIAL_TIME, "00:42:15"), Map.entry(CertificateField.GUN_TIME, "00:42:15"),
                Map.entry(CertificateField.CHIP_TIME, "00:41:58"), Map.entry(CertificateField.OFFICIAL_PLACE, "25 место"),
                Map.entry(CertificateField.OVERALL_PLACE, "25 место"), Map.entry(CertificateField.GENDER_PLACE, "20 место"),
                Map.entry(CertificateField.CATEGORY_PLACE, "5 место"), Map.entry(CertificateField.CATEGORY_NAME, "М35–39"),
                Map.entry(CertificateField.BIB, "TEST-101"));
    }

    private void audit(String actor, AuditEntityType type, Long id, String field, String oldValue, String newValue) {
        AdminChangeLog log = new AdminChangeLog(); log.setActor(actor); log.setEntityType(type); log.setEntityId(id);
        log.setFieldName(field); log.setOldValue(oldValue); log.setNewValue(newValue); auditRepository.save(log);
    }

    public record GeneratedCertificate(byte[] bytes, String filename) { }
}

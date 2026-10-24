package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CertificateField;

import java.util.List;

public record CertificateAvailabilityDto(
        boolean enabled,
        List<TemplateOption> templates,
        List<FieldOption> fields
) {
    public CertificateAvailabilityDto {
        templates = List.copyOf(templates);
        fields = List.copyOf(fields);
    }

    public record TemplateOption(Long id, String displayName) { }
    public record FieldOption(CertificateField field, String label, boolean available) { }
}

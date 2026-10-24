package ru.sportsresults.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.CertificateTextAlignment;

import java.math.BigDecimal;

public record CertificateTemplateLayoutDto(
        @NotNull BigDecimal participantX,
        @NotNull BigDecimal participantY,
        @NotNull @DecimalMin("1") BigDecimal participantWidth,
        @NotNull @DecimalMin("1") BigDecimal participantFontSize,
        @NotNull @DecimalMin("1") BigDecimal participantMinFontSize,
        @NotNull CertificateTextAlignment participantAlignment,
        @NotNull BigDecimal eventX,
        @NotNull BigDecimal eventY,
        @NotNull @DecimalMin("1") BigDecimal eventWidth,
        @NotNull @DecimalMin("1") BigDecimal eventFontSize,
        @NotNull @DecimalMin("1") BigDecimal eventMinFontSize,
        @NotNull @DecimalMin("1") BigDecimal eventLineSpacing,
        @NotNull CertificateTextAlignment eventAlignment,
        @NotNull BigDecimal dynamicX,
        @NotNull BigDecimal dynamicY,
        @NotNull @DecimalMin("1") BigDecimal dynamicWidth,
        @NotNull @DecimalMin("1") BigDecimal dynamicMaxHeight,
        @NotNull @DecimalMin("1") BigDecimal dynamicFontSize,
        @NotNull @DecimalMin("1") BigDecimal dynamicMinFontSize,
        @NotNull @DecimalMin("1") BigDecimal dynamicLineSpacing,
        @NotNull CertificateTextAlignment dynamicAlignment
) {
}

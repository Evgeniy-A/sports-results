package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EventStartDefinitionRequest(
        Long templateStartId,
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero BigDecimal distanceMeters,
        @Size(max = 255) String sourceCode,
        Boolean publicVisible,
        @Valid UpdateAwardPolicyRequest awardPolicy
) {
}

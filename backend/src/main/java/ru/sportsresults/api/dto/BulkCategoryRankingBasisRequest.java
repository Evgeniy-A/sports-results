package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.CategoryRankingBasisBulkAction;

public record BulkCategoryRankingBasisRequest(
        @NotNull CategoryRankingBasisBulkAction action
) {
}

package ru.sportsresults.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.JournalSharePermission;

import java.time.Instant;
import java.util.UUID;

public final class JournalShareDtos {

    private JournalShareDtos() {
    }

    public record CreateRequest(
            @Positive Long eventSeriesId,
            @Min(2000) @Max(2200) Integer year,
            @Size(max = 255) String cityScope,
            @NotNull JournalSharePermission permission,
            boolean canSyncNow,
            @Size(max = 160) String label,
            @Min(1) @Max(365) Integer lifetimeDays,
            boolean noExpiry
    ) {
        @AssertTrue(message = "Choose either lifetimeDays or noExpiry")
        public boolean isLifetimeValid() {
            return noExpiry ? lifetimeDays == null : lifetimeDays != null;
        }

        @AssertTrue(message = "Synchronization requires EDIT permission")
        public boolean isSyncPermissionValid() {
            return !canSyncNow || permission == JournalSharePermission.EDIT;
        }
    }

    public record Grant(
            UUID id,
            Long eventSeriesId,
            String eventSeriesName,
            int year,
            String cityScope,
            JournalSharePermission permission,
            boolean canSyncNow,
            String label,
            Instant expiresAt,
            Instant createdAt,
            String createdBy,
            Instant revokedAt,
            String revokedBy,
            Instant lastAccessedAt,
            long accessCount
    ) {
    }

    public record Created(Grant grant, String sharePath) {
    }

    public record ExchangeRequest(@NotBlank @Size(min = 40, max = 128) String token) {
    }

    public record ExchangeResponse(String sessionToken, Instant sessionExpiresAt, Context context) {
    }

    public record Context(
            UUID grantId,
            String label,
            Long eventSeriesId,
            String eventSeriesName,
            int year,
            String cityScope,
            JournalSharePermission permission,
            boolean canSyncNow,
            Instant grantExpiresAt
    ) {
    }
}

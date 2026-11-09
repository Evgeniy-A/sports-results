package ru.sportsresults.api.dto;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.JournalSharePermission;

import static org.assertj.core.api.Assertions.assertThat;

class JournalShareDtosTest {

    @Test
    void validatesExplicitLifetimeXorNoExpiryAndSyncPermission() {
        assertThat(request(JournalSharePermission.EDIT, false, 30, false).isLifetimeValid()).isTrue();
        assertThat(request(JournalSharePermission.EDIT, false, null, true).isLifetimeValid()).isTrue();
        assertThat(request(JournalSharePermission.EDIT, false, 30, true).isLifetimeValid()).isFalse();
        assertThat(request(JournalSharePermission.EDIT, false, null, false).isLifetimeValid()).isFalse();
        assertThat(request(JournalSharePermission.VIEW, true, 30, false).isSyncPermissionValid()).isFalse();
        assertThat(request(JournalSharePermission.EDIT, true, 30, false).isSyncPermissionValid()).isTrue();
    }

    private static JournalShareDtos.CreateRequest request(
            JournalSharePermission permission,
            boolean canSyncNow,
            Integer lifetimeDays,
            boolean noExpiry
    ) {
        return new JournalShareDtos.CreateRequest(
                1L, 2026, null, permission, canSyncNow, "Тест", lifetimeDays, noExpiry
        );
    }
}

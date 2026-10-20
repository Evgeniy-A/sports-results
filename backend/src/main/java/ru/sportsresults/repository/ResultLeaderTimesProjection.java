package ru.sportsresults.repository;

import java.time.Duration;

public record ResultLeaderTimesProjection(
        String gender,
        Duration gunLeaderTime,
        Duration chipLeaderTime
) {
}

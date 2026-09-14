package ru.sportsresults.service;

public record AgeCategoryResolution<T>(
        T category,
        AgeCategoryBranch branch,
        AgeCategoryResolutionReason reason,
        String blockingCode,
        String blockingMessage
) {
    public boolean blocked() {
        return blockingCode != null;
    }
}

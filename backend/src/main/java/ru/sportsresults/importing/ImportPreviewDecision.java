package ru.sportsresults.importing;

public enum ImportPreviewDecision {
    NEW,
    EXISTING_UNCHANGED,
    EXISTING_CHANGED,
    RETIRED,
    AMBIGUOUS,
    CONFLICT,
    INVALID,
    DUPLICATE_IN_FILE,
    OUT_OF_SCOPE
}

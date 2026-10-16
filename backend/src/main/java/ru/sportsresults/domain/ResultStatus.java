package ru.sportsresults.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ResultStatus {
    FINISHED("finished"),
    RUNNING("running"),
    NOT_STARTED("notstarted"),
    DISQUALIFIED("disqualified"),
    QUARANTINE("quarantine");

    private final String code;

    ResultStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .map(ResultStatus::code)
                .filter(normalized::equals)
                .findFirst();
    }

    public static String allowedCodes() {
        return Arrays.stream(values())
                .map(ResultStatus::code)
                .collect(Collectors.joining(", "));
    }
}

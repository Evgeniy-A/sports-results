package ru.sportsresults.importing;

import java.util.Objects;

public record SourceField<T>(SourceFieldState state, T value) {

    public SourceField {
        Objects.requireNonNull(state, "state");
        if (state == SourceFieldState.VALUE) {
            Objects.requireNonNull(value, "VALUE source field requires a value");
        } else if (value != null) {
            throw new IllegalArgumentException(state + " source field must not contain a value");
        }
    }

    public static <T> SourceField<T> absent() {
        return new SourceField<>(SourceFieldState.ABSENT, null);
    }

    public static <T> SourceField<T> empty() {
        return new SourceField<>(SourceFieldState.EMPTY, null);
    }

    public static <T> SourceField<T> value(T value) {
        return new SourceField<>(SourceFieldState.VALUE, value);
    }

    public boolean isAbsent() {
        return state == SourceFieldState.ABSENT;
    }

    public boolean isEmpty() {
        return state == SourceFieldState.EMPTY;
    }

    public boolean hasValue() {
        return state == SourceFieldState.VALUE;
    }

    public T valueOrNull() {
        return hasValue() ? value : null;
    }
}

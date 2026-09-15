package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RacePresentationTest {

    @Test
    void buildsSelfContainedNameFromBusinessFormatAndRace() {
        assertThat(RacePresentation.effectiveName("Масс-старт", "10 км"))
                .isEqualTo("Масс-старт 10 км");
    }

    @Test
    void doesNotDuplicateFormatAlreadyPresentInRaceName() {
        assertThat(RacePresentation.effectiveName("Масс-старт", "Масс-старт 10 км"))
                .isEqualTo("Масс-старт 10 км");
        assertThat(RacePresentation.effectiveName("Чемпионат", "Чемпионат — финал"))
                .isEqualTo("Чемпионат — финал");
    }

    @Test
    void omitsTechnicalDefaultFormatNames() {
        assertThat(RacePresentation.effectiveName("Основной формат", "10 км")).isEqualTo("10 км");
        assertThat(RacePresentation.effectiveName("Основной", "42.2 км")).isEqualTo("42.2 км");
        assertThat(RacePresentation.effectiveName("default", "Детский забег")).isEqualTo("Детский забег");
    }
}

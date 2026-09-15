package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator generator = new SlugGenerator();

    @Test
    void transliteratesRussianNamesIntoReadableSlugs() {
        assertThat(generator.slugify("Гонка Героев", "series")).isEqualTo("gonka-geroev");
        assertThat(generator.slugify("М52", "series")).isEqualTo("m52");
        assertThat(generator.slugify("Гонка Героев — Казань 2027", "event"))
                .isEqualTo("gonka-geroev-kazan-2027");
    }

    @Test
    void normalizesSeparatorsAndUnsupportedCharacters() {
        assertThat(generator.slugify("  Trail / RUN --- 2027!  ", "event"))
                .isEqualTo("trail-run-2027");
    }

    @Test
    void addsTheNextAvailableNumericSuffix() {
        Set<String> existing = Set.of("gonka-geroev", "gonka-geroev-2");

        assertThat(generator.uniqueSlug("Гонка Героев", "series", existing::contains))
                .isEqualTo("gonka-geroev-3");
    }

    @Test
    void keepsGeneratedSlugsWithinTheDatabaseColumnLength() {
        String longName = "Гонка ".repeat(80);
        String base = generator.slugify(longName, "event");
        String slug = generator.uniqueSlug(longName, "event", Set.of(base)::contains);

        assertThat(slug).hasSizeLessThanOrEqualTo(SlugGenerator.MAX_LENGTH);
        assertThat(slug).endsWith("-2");
    }
}

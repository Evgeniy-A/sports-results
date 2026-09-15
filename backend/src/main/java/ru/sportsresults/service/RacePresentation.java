package ru.sportsresults.service;

import ru.sportsresults.domain.Race;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Transitional Race-only presentation shared by the V24 compatibility API.
 * The normalization rules intentionally match the planned V25 data migration.
 */
public final class RacePresentation {
    private static final Set<String> TECHNICAL_FORMAT_NAMES = Set.of(
            "основной формат",
            "основной",
            "default"
    );
    private static final Set<Character> NAME_BOUNDARIES = Set.of(
            ' ', '-', '—', '/', ':', '·', ',', '('
    );

    private RacePresentation() {
    }

    public static String effectiveName(Race race) {
        return effectiveName(race.getSportFormat().getDisplayName(), race.getName());
    }

    public static String effectiveName(String formatName, String raceName) {
        String normalizedRaceName = normalize(raceName);
        String normalizedFormatName = normalize(formatName);
        String displayRaceName = raceName.strip();

        if (normalizedFormatName.isEmpty()
                || isTechnicalFormatName(formatName)
                || normalizedRaceName.equals(normalizedFormatName)
                || startsWithFormat(normalizedRaceName, normalizedFormatName)) {
            return displayRaceName;
        }
        return formatName.strip() + " " + displayRaceName;
    }

    public static boolean effectivePublicVisible(Race race) {
        return race.isPublicVisible() && race.getSportFormat().isPublicVisible();
    }

    public static boolean isTechnicalFormatName(String formatName) {
        return TECHNICAL_FORMAT_NAMES.contains(normalize(formatName));
    }

    public static List<Race> stableFlatOrder(Collection<Race> races) {
        return races.stream()
                .sorted(Comparator
                        .comparingInt((Race race) -> race.getSportFormat().getDisplayOrder())
                        .thenComparingInt(Race::getDisplayOrder)
                        .thenComparing(Race::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static boolean startsWithFormat(String raceName, String formatName) {
        if (!raceName.startsWith(formatName) || raceName.length() == formatName.length()) {
            return false;
        }
        return NAME_BOUNDARIES.contains(raceName.charAt(formatName.length()));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

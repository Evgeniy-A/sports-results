package ru.sportsresults.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class SlugGenerator {

    static final int MAX_LENGTH = 160;

    public String uniqueSlug(String source, String fallback, Predicate<String> alreadyExists) {
        String base = slugify(source, fallback);
        String candidate = base;
        int suffixNumber = 2;
        while (alreadyExists.test(candidate)) {
            String suffix = "-" + suffixNumber++;
            candidate = trimToLength(base, MAX_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
    }

    public String slugify(String source, String fallback) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        StringBuilder transliterated = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            transliterated.append(transliterate(value.charAt(index)));
        }
        String normalized = Normalizer.normalize(transliterated, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String safeFallback = fallback.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return trimToLength(normalized.isEmpty() ? safeFallback : normalized, MAX_LENGTH);
    }

    private static String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength).replaceAll("-+$", "");
    }

    private static String transliterate(char value) {
        return switch (value) {
            case 'а' -> "a";
            case 'б' -> "b";
            case 'в' -> "v";
            case 'г' -> "g";
            case 'д' -> "d";
            case 'е' -> "e";
            case 'ё' -> "yo";
            case 'ж' -> "zh";
            case 'з' -> "z";
            case 'и' -> "i";
            case 'й' -> "y";
            case 'к' -> "k";
            case 'л' -> "l";
            case 'м' -> "m";
            case 'н' -> "n";
            case 'о' -> "o";
            case 'п' -> "p";
            case 'р' -> "r";
            case 'с' -> "s";
            case 'т' -> "t";
            case 'у' -> "u";
            case 'ф' -> "f";
            case 'х' -> "kh";
            case 'ц' -> "ts";
            case 'ч' -> "ch";
            case 'ш' -> "sh";
            case 'щ' -> "shch";
            case 'ъ', 'ь' -> "";
            case 'ы' -> "y";
            case 'э' -> "e";
            case 'ю' -> "yu";
            case 'я' -> "ya";
            default -> Character.toString(value);
        };
    }
}

package ru.sportsresults.importing;

import java.text.Normalizer;
import java.util.Locale;

public final class ImportHeaderNormalizer {

    private ImportHeaderNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[_.\\-]+", " ")
                .replaceAll("\\s+", " ");
    }
}

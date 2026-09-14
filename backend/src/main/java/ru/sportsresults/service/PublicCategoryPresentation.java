package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.domain.Category;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class PublicCategoryPresentation {

    private static final Set<String> BASE_MALE = Set.of(
            "18+male", "male18+", "18+men", "men18+", "18+m", "m18+",
            "18+м", "м18+", "18+мужчины", "мужчины18+", "18+мужской", "мужской18+"
    );
    private static final Set<String> BASE_FEMALE = Set.of(
            "18+female", "female18+", "18+women", "women18+", "18+f", "f18+",
            "18+ж", "ж18+", "18+женщины", "женщины18+", "18+женский", "женский18+"
    );

    public String publicName(Category category) {
        BaseKind kind = baseKind(category.getSourceName());
        if (kind == BaseKind.MALE) return "18+ М";
        if (kind == BaseKind.FEMALE) return "18+ Ж";
        return localizeGender(category.getDisplayName());
    }

    public String publicName(String displayName) {
        BaseKind kind = baseKind(displayName);
        if (kind == BaseKind.MALE) return "18+ М";
        if (kind == BaseKind.FEMALE) return "18+ Ж";
        return localizeGender(displayName);
    }

    public String publicName(String sourceName, String displayName) {
        BaseKind kind = baseKind(sourceName);
        if (kind == BaseKind.MALE) return "18+ М";
        if (kind == BaseKind.FEMALE) return "18+ Ж";
        return localizeGender(displayName);
    }

    boolean isBaseCategory(String value) {
        return baseKind(value) != BaseKind.OTHER;
    }

    private static BaseKind baseKind(String value) {
        String normalized = normalize(value);
        if (BASE_MALE.contains(normalized)) return BaseKind.MALE;
        if (BASE_FEMALE.contains(normalized)) return BaseKind.FEMALE;
        return BaseKind.OTHER;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[\\s._/()\\-]+", "");
    }

    private static String localizeGender(String value) {
        if (value == null) return null;
        return value
                .replaceAll("(?iu)\\bFemale\\b", "Ж")
                .replaceAll("(?iu)\\bWomen\\b", "Ж")
                .replaceAll("(?iu)\\bMale\\b", "М")
                .replaceAll("(?iu)\\bMen\\b", "М");
    }

    private enum BaseKind {
        MALE,
        FEMALE,
        OTHER
    }
}

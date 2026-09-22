package ru.sportsresults.importing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ImportColumnAliases {

    private static final Map<String, Set<CanonicalImportField>> ALIASES = aliases();

    private ImportColumnAliases() {}

    public static Set<CanonicalImportField> candidates(String header) {
        return ALIASES.getOrDefault(ImportHeaderNormalizer.normalize(header), Set.of());
    }

    public static Map<String, Set<CanonicalImportField>> all() {
        return ALIASES;
    }

    private static Map<String, Set<CanonicalImportField>> aliases() {
        Map<CanonicalImportField, Set<String>> byField = new EnumMap<>(CanonicalImportField.class);
        add(byField, CanonicalImportField.BIB, "стартовый номер", "номер", "bib", "bib number", "bib_number", "dorsal");
        add(byField, CanonicalImportField.FIRST_NAME, "имя", "first name", "firstname", "given name");
        add(byField, CanonicalImportField.LAST_NAME, "фамилия", "surname", "last name", "lastname", "family name");
        add(byField, CanonicalImportField.FULL_NAME, "фио", "участник", "full name", "fullname", "name");
        add(byField, CanonicalImportField.GENDER, "пол", "gender", "sex");
        add(byField, CanonicalImportField.BIRTH_DATE, "дата рождения", "др", "dob", "birth date", "birthdate", "date of birth");
        add(byField, CanonicalImportField.STATUS, "статус", "status", "result status");
        add(byField, CanonicalImportField.GUN_TIME, "официальное время", "gun time", "gun_time", "gross time", "times.official_:::finish:::");
        add(byField, CanonicalImportField.CHIP_TIME, "чистое время", "chip time", "chip_time", "net time", "times.real_:::finish:::");
        add(byField, CanonicalImportField.CATEGORY, "категория", "category", "age category", "age group", "agegrp");
        add(byField, CanonicalImportField.CLUSTER, "кластер", "cluster", "wave", "start wave", "clustercode", "clustername", "clustersourcename");
        add(byField, CanonicalImportField.RACE, "старт", "гонка", "дистанция", "start", "race", "event", "distance", "discipline", "format");
        add(byField, CanonicalImportField.OVERALL_PLACE, "абсолютное место", "overall place", "rankings_:::full-1:::");
        add(byField, CanonicalImportField.GENDER_PLACE, "место по полу", "gender place", "rankings.gen_:::full-1:::");
        add(byField, CanonicalImportField.CATEGORY_PLACE, "место в категории", "category place", "rankings.cat_:::full-1:::");
        add(byField, CanonicalImportField.NET_OVERALL_PLACE, "абсолютное место по чистому времени", "net overall place", "netrankings_:::full-1:::");
        add(byField, CanonicalImportField.NET_GENDER_PLACE, "место по полу по чистому времени", "net gender place", "netrankings.gen_:::full-1:::");
        add(byField, CanonicalImportField.NET_CATEGORY_PLACE, "место в категории по чистому времени", "net category place", "netrankings.cat_:::full-1:::");
        // Deliberately ambiguous: a bare "time" must never silently choose gun or chip time.
        add(byField, CanonicalImportField.GUN_TIME, "время", "time");
        add(byField, CanonicalImportField.CHIP_TIME, "время", "time");

        Map<String, Set<CanonicalImportField>> result = new LinkedHashMap<>();
        byField.forEach((field, names) -> names.forEach(name -> result
                .computeIfAbsent(ImportHeaderNormalizer.normalize(name), ignored -> new LinkedHashSet<>())
                .add(field)));
        result.replaceAll((key, value) -> Collections.unmodifiableSet(value));
        return Collections.unmodifiableMap(result);
    }

    private static void add(
            Map<CanonicalImportField, Set<String>> aliases,
            CanonicalImportField field,
            String... values
    ) {
        Collections.addAll(aliases.computeIfAbsent(field, ignored -> new LinkedHashSet<>()), values);
    }
}

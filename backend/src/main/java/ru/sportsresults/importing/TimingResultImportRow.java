package ru.sportsresults.importing;

import ru.sportsresults.domain.RegistrationEntryKind;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

public record TimingResultImportRow(
        int sourceRowNumber,
        String sourceRowHash,
        SourceField<String> firstNameSource,
        SourceField<String> lastNameSource,
        SourceField<String> genderSource,
        SourceField<LocalDate> birthDateSource,
        String raceCode,
        String bib,
        SourceField<String> categorySource,
        SourceField<String> clusterCodeSource,
        SourceField<String> clusterNameSource,
        SourceField<String> clusterSourceNameSource,
        String status,
        RegistrationEntryKind entryKind,
        SourceField<Duration> gunTimeSource,
        SourceField<Duration> chipTimeSource,
        SourceField<Integer> overallPlaceSource,
        SourceField<Integer> genderPlaceSource,
        SourceField<Integer> categoryPlaceSource,
        SourceField<Integer> netOverallPlaceSource,
        SourceField<Integer> netGenderPlaceSource,
        SourceField<Integer> netCategoryPlaceSource
) {
    public TimingResultImportRow {
        Objects.requireNonNull(firstNameSource, "firstNameSource");
        Objects.requireNonNull(lastNameSource, "lastNameSource");
        Objects.requireNonNull(genderSource, "genderSource");
        Objects.requireNonNull(birthDateSource, "birthDateSource");
        Objects.requireNonNull(categorySource, "categorySource");
        Objects.requireNonNull(clusterCodeSource, "clusterCodeSource");
        Objects.requireNonNull(clusterNameSource, "clusterNameSource");
        Objects.requireNonNull(clusterSourceNameSource, "clusterSourceNameSource");
        Objects.requireNonNull(entryKind, "entryKind");
        Objects.requireNonNull(gunTimeSource, "gunTimeSource");
        Objects.requireNonNull(chipTimeSource, "chipTimeSource");
        Objects.requireNonNull(overallPlaceSource, "overallPlaceSource");
        Objects.requireNonNull(genderPlaceSource, "genderPlaceSource");
        Objects.requireNonNull(categoryPlaceSource, "categoryPlaceSource");
        Objects.requireNonNull(netOverallPlaceSource, "netOverallPlaceSource");
        Objects.requireNonNull(netGenderPlaceSource, "netGenderPlaceSource");
        Objects.requireNonNull(netCategoryPlaceSource, "netCategoryPlaceSource");
    }

    public String firstName() { return firstNameSource.valueOrNull(); }
    public String lastName() { return lastNameSource.valueOrNull(); }
    public String gender() { return genderSource.valueOrNull(); }
    public LocalDate birthDate() { return birthDateSource.valueOrNull(); }
    public String category() { return categorySource.valueOrNull(); }
    public String clusterCode() { return clusterCodeSource.valueOrNull(); }
    public String clusterName() { return clusterNameSource.valueOrNull(); }
    public String clusterSourceName() { return clusterSourceNameSource.valueOrNull(); }
    public Duration gunTime() { return gunTimeSource.valueOrNull(); }
    public Duration chipTime() { return chipTimeSource.valueOrNull(); }
    public Integer overallPlace() { return overallPlaceSource.valueOrNull(); }
    public Integer genderPlace() { return genderPlaceSource.valueOrNull(); }
    public Integer categoryPlace() { return categoryPlaceSource.valueOrNull(); }
    public Integer netOverallPlace() { return netOverallPlaceSource.valueOrNull(); }
    public Integer netGenderPlace() { return netGenderPlaceSource.valueOrNull(); }
    public Integer netCategoryPlace() { return netCategoryPlaceSource.valueOrNull(); }
}

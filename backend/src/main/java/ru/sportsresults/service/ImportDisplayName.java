package ru.sportsresults.service;

import ru.sportsresults.importing.TimingResultImportRow;

final class ImportDisplayName {

    private ImportDisplayName() {
    }

    static String from(TimingResultImportRow row) {
        String firstName = normalize(row.firstName());
        String lastName = normalize(row.lastName());
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) {
            return firstName;
        }
        if (lastName != null) {
            return lastName;
        }
        if (row.bib() != null) {
            return "Bib " + row.bib();
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

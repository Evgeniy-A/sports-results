package ru.sportsresults.importing;

public enum CanonicalImportField {
    BIB("Стартовый номер", true),
    FIRST_NAME("Имя", false),
    LAST_NAME("Фамилия", false),
    FULL_NAME("ФИО", false),
    GENDER("Пол", false),
    BIRTH_DATE("Дата рождения", false),
    STATUS("Статус", true),
    GUN_TIME("Официальное время", false),
    CHIP_TIME("Чистое время", false),
    CATEGORY("Категория", false),
    CLUSTER("Кластер", false),
    RACE("Старт", false),
    OVERALL_PLACE("Абсолютное место", false),
    GENDER_PLACE("Место по полу", false),
    CATEGORY_PLACE("Место в категории", false),
    NET_OVERALL_PLACE("Абсолютное место по чистому времени", false),
    NET_GENDER_PLACE("Место по полу по чистому времени", false),
    NET_CATEGORY_PLACE("Место в категории по чистому времени", false);

    private final String displayName;
    private final boolean required;

    CanonicalImportField(String displayName, boolean required) {
        this.displayName = displayName;
        this.required = required;
    }

    public String displayName() { return displayName; }
    public boolean required() { return required; }
}

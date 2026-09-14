ALTER TABLE award_policies
    ADD COLUMN age_calculation_mode VARCHAR(32) NOT NULL DEFAULT 'EVENT_DATE';

ALTER TABLE award_policies
    ADD CONSTRAINT ck_award_policies_age_calculation_mode
        CHECK (age_calculation_mode IN ('EVENT_DATE', 'END_OF_EVENT_YEAR'));

ALTER TABLE categories
    ADD COLUMN min_age INTEGER,
    ADD COLUMN max_age INTEGER,
    ADD COLUMN gender VARCHAR(16),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE categories
    ADD CONSTRAINT ck_categories_age_range CHECK (
        (min_age IS NULL AND max_age IS NULL)
        OR (min_age IS NOT NULL AND min_age >= 0 AND (max_age IS NULL OR max_age >= min_age))
    ),
    ADD CONSTRAINT ck_categories_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'));

ALTER TABLE registrations
    ADD COLUMN source_category VARCHAR(255);

UPDATE registrations registration
SET source_category = category.source_name
FROM categories category
WHERE category.id = registration.category_id;

CREATE INDEX ix_categories_race_resolution
    ON categories (race_id, enabled, gender, min_age, max_age);

CREATE INDEX ix_registrations_race_source_category
    ON registrations (race_id, source_category)
    WHERE source_category IS NOT NULL;

ALTER TABLE admin_change_logs
    DROP CONSTRAINT ck_admin_change_logs_entity_type;

ALTER TABLE admin_change_logs
    ADD CONSTRAINT ck_admin_change_logs_entity_type CHECK (
        entity_type IN ('REGISTRATION', 'RESULT', 'AWARD_POLICY', 'EVENT_SERIES', 'EVENT', 'RACE', 'CATEGORY')
    );

ALTER TABLE races
    ADD COLUMN public_ranking_basis VARCHAR(32) NOT NULL DEFAULT 'CHIP_TIME',
    ADD CONSTRAINT ck_races_public_ranking_basis
        CHECK (public_ranking_basis IN ('GUN_TIME', 'CHIP_TIME'));

ALTER TABLE admin_change_logs DROP CONSTRAINT ck_admin_change_logs_entity_type;
ALTER TABLE admin_change_logs ADD CONSTRAINT ck_admin_change_logs_entity_type
    CHECK (entity_type IN ('REGISTRATION', 'RESULT', 'AWARD_POLICY', 'EVENT_SERIES', 'EVENT', 'RACE'));

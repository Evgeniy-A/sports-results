ALTER TABLE result_issue_work_items
    ADD COLUMN base_display_name VARCHAR(320),
    ADD COLUMN desired_display_name VARCHAR(320),
    ADD COLUMN base_gender VARCHAR(32),
    ADD COLUMN desired_gender VARCHAR(32),
    ADD COLUMN base_birth_date DATE,
    ADD COLUMN desired_birth_date DATE,
    ADD COLUMN base_bib VARCHAR(64),
    ADD COLUMN desired_bib VARCHAR(64),
    ADD COLUMN base_race_id BIGINT,
    ADD COLUMN desired_race_id BIGINT,
    ADD CONSTRAINT fk_result_issue_work_items_base_race
        FOREIGN KEY (base_race_id) REFERENCES races(id),
    ADD CONSTRAINT fk_result_issue_work_items_desired_race
        FOREIGN KEY (desired_race_id) REFERENCES races(id),
    ADD CONSTRAINT ck_result_issue_work_items_base_display_name
        CHECK (base_display_name IS NULL OR char_length(trim(base_display_name)) BETWEEN 1 AND 320),
    ADD CONSTRAINT ck_result_issue_work_items_desired_display_name
        CHECK (desired_display_name IS NULL OR char_length(trim(desired_display_name)) BETWEEN 1 AND 320);

CREATE INDEX ix_result_issue_work_items_base_race
    ON result_issue_work_items (base_race_id, id);

CREATE INDEX ix_result_issue_work_items_desired_race
    ON result_issue_work_items (desired_race_id, id);

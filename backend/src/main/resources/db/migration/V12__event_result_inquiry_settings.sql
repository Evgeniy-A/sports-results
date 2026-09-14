ALTER TABLE events
    ADD COLUMN result_inquiry_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN result_inquiry_window_days INTEGER,
    ADD COLUMN result_inquiry_email VARCHAR(320),
    ADD CONSTRAINT ck_events_result_inquiry_window
        CHECK (result_inquiry_window_days IS NULL OR result_inquiry_window_days > 0),
    ADD CONSTRAINT ck_events_result_inquiry_email
        CHECK (result_inquiry_email IS NULL OR btrim(result_inquiry_email) <> ''),
    ADD CONSTRAINT ck_events_result_inquiry_enabled_config
        CHECK (
            result_inquiry_enabled = FALSE
            OR (
                result_inquiry_window_days IS NOT NULL
                AND result_inquiry_email IS NOT NULL
                AND btrim(result_inquiry_email) <> ''
            )
        );

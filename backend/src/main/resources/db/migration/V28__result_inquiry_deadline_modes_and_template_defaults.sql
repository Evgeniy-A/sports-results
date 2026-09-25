ALTER TABLE events
    DROP CONSTRAINT ck_events_result_inquiry_enabled_config,
    ADD COLUMN result_inquiry_deadline_mode VARCHAR(32) NOT NULL DEFAULT 'AFTER_EVENT_DAYS',
    ADD COLUMN result_inquiry_fixed_date DATE,
    ADD CONSTRAINT ck_events_result_inquiry_deadline_mode
        CHECK (result_inquiry_deadline_mode IN ('AFTER_EVENT_DAYS', 'FIXED_DATE')),
    ADD CONSTRAINT ck_events_result_inquiry_enabled_config
        CHECK (
            result_inquiry_enabled = FALSE
            OR (
                result_inquiry_email IS NOT NULL
                AND btrim(result_inquiry_email) <> ''
                AND (
                    (result_inquiry_deadline_mode = 'AFTER_EVENT_DAYS'
                        AND result_inquiry_window_days IS NOT NULL)
                    OR
                    (result_inquiry_deadline_mode = 'FIXED_DATE'
                        AND result_inquiry_fixed_date IS NOT NULL)
                )
            )
        );

ALTER TABLE event_series
    ADD COLUMN default_result_inquiry_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN default_result_inquiry_deadline_mode VARCHAR(32) NOT NULL DEFAULT 'AFTER_EVENT_DAYS',
    ADD COLUMN default_result_inquiry_window_days INTEGER,
    ADD COLUMN default_result_inquiry_fixed_date DATE,
    ADD COLUMN default_result_inquiry_email VARCHAR(320),
    ADD CONSTRAINT ck_event_series_result_inquiry_deadline_mode
        CHECK (default_result_inquiry_deadline_mode IN ('AFTER_EVENT_DAYS', 'FIXED_DATE')),
    ADD CONSTRAINT ck_event_series_result_inquiry_window
        CHECK (default_result_inquiry_window_days IS NULL OR default_result_inquiry_window_days > 0),
    ADD CONSTRAINT ck_event_series_result_inquiry_email
        CHECK (default_result_inquiry_email IS NULL OR btrim(default_result_inquiry_email) <> ''),
    ADD CONSTRAINT ck_event_series_result_inquiry_enabled_config
        CHECK (
            default_result_inquiry_enabled = FALSE
            OR (
                default_result_inquiry_email IS NOT NULL
                AND btrim(default_result_inquiry_email) <> ''
                AND (
                    (default_result_inquiry_deadline_mode = 'AFTER_EVENT_DAYS'
                        AND default_result_inquiry_window_days IS NOT NULL)
                    OR
                    (default_result_inquiry_deadline_mode = 'FIXED_DATE'
                        AND default_result_inquiry_fixed_date IS NOT NULL)
                )
            )
        );

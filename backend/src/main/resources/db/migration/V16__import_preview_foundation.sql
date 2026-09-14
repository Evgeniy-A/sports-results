ALTER TABLE events
    ADD COLUMN result_data_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_events_result_data_revision CHECK (result_data_revision >= 0);

DROP INDEX uk_import_batches_successful_event_file;

CREATE TABLE import_operations (
    id UUID PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    operation_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PREVIEWED',
    source_filename VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    base_revision BIGINT NOT NULL,
    plan_digest VARCHAR(64) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    preview_summary JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT ck_import_operations_mode
        CHECK (operation_mode IN ('ADD_NEW', 'UPDATE_EXISTING')),
    CONSTRAINT ck_import_operations_status
        CHECK (status IN ('PREVIEWED', 'APPLYING', 'APPLIED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_import_operations_filename CHECK (btrim(source_filename) <> ''),
    CONSTRAINT ck_import_operations_file_hash CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_import_operations_base_revision CHECK (base_revision >= 0),
    CONSTRAINT ck_import_operations_plan_digest CHECK (plan_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_import_operations_actor CHECK (btrim(created_by) <> '')
);

CREATE TABLE import_operation_races (
    operation_id UUID NOT NULL REFERENCES import_operations(id) ON DELETE CASCADE,
    race_id BIGINT NOT NULL REFERENCES races(id),
    PRIMARY KEY (operation_id, race_id)
);

CREATE INDEX ix_import_operations_event_created
    ON import_operations(event_id, created_at DESC);

CREATE INDEX ix_import_operations_status_expiry
    ON import_operations(status, expires_at)
    WHERE status = 'PREVIEWED';

CREATE INDEX ix_import_operation_races_race
    ON import_operation_races(race_id, operation_id);

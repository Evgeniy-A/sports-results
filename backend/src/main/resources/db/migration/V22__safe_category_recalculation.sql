ALTER TABLE races
    ADD COLUMN result_recalculation_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE result_recalculation_operations (
    id UUID PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PREVIEWED',
    base_revision BIGINT NOT NULL,
    configuration_digest VARCHAR(64) NOT NULL,
    plan_digest VARCHAR(64) NOT NULL,
    preview_summary JSONB NOT NULL,
    current_count INTEGER NOT NULL,
    changed_count INTEGER NOT NULL,
    blocking_count INTEGER NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    applied_by VARCHAR(160),
    applied_at TIMESTAMPTZ,
    new_revision BIGINT,
    CONSTRAINT ck_result_recalculation_status
        CHECK (status IN ('PREVIEWED', 'APPLIED', 'EXPIRED')),
    CONSTRAINT ck_result_recalculation_base_revision CHECK (base_revision >= 0),
    CONSTRAINT ck_result_recalculation_configuration_digest
        CHECK (configuration_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_result_recalculation_plan_digest
        CHECK (plan_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_result_recalculation_counts CHECK (
        current_count >= 0 AND changed_count >= 0 AND blocking_count >= 0
    ),
    CONSTRAINT ck_result_recalculation_created_by CHECK (btrim(created_by) <> ''),
    CONSTRAINT ck_result_recalculation_applied_by CHECK (
        applied_by IS NULL OR btrim(applied_by) <> ''
    ),
    CONSTRAINT ck_result_recalculation_applied_state CHECK (
        (status = 'APPLIED' AND applied_by IS NOT NULL AND applied_at IS NOT NULL AND new_revision IS NOT NULL)
        OR
        (status <> 'APPLIED' AND applied_by IS NULL AND applied_at IS NULL AND new_revision IS NULL)
    )
);

CREATE TABLE result_recalculation_operation_races (
    operation_id UUID NOT NULL REFERENCES result_recalculation_operations(id) ON DELETE CASCADE,
    race_id BIGINT NOT NULL REFERENCES races(id),
    PRIMARY KEY (operation_id, race_id)
);

CREATE INDEX ix_result_recalculation_operations_event_created
    ON result_recalculation_operations(event_id, created_at DESC);

CREATE INDEX ix_result_recalculation_operations_status_expiry
    ON result_recalculation_operations(status, expires_at)
    WHERE status = 'PREVIEWED';

CREATE INDEX ix_result_recalculation_operation_races_race
    ON result_recalculation_operation_races(race_id, operation_id);

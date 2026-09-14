ALTER TABLE import_operations
    DROP CONSTRAINT ck_import_operations_mode,
    ADD CONSTRAINT ck_import_operations_mode
        CHECK (operation_mode IN ('ADD_NEW', 'UPDATE_EXISTING', 'EMERGENCY_REPLACE')),
    ADD COLUMN retired_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN archived_issue_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_import_operations_emergency_counts CHECK (
        retired_count >= 0 AND archived_issue_count >= 0
    );

ALTER TABLE registrations
    ADD COLUMN retired_at TIMESTAMPTZ,
    ADD COLUMN retired_by_import_operation_id UUID REFERENCES import_operations(id),
    ADD CONSTRAINT ck_registrations_retirement_metadata CHECK (
        (retired_at IS NULL AND retired_by_import_operation_id IS NULL)
        OR
        (retired_at IS NOT NULL AND retired_by_import_operation_id IS NOT NULL)
    );

CREATE INDEX ix_registrations_current_race_bib
    ON registrations(race_id, bib, id)
    WHERE retired_at IS NULL;

CREATE INDEX ix_registrations_retired_operation
    ON registrations(retired_by_import_operation_id, id)
    WHERE retired_by_import_operation_id IS NOT NULL;

ALTER TABLE import_operation_items
    ALTER COLUMN source_row_number DROP NOT NULL,
    DROP CONSTRAINT ck_import_operation_items_source_row,
    DROP CONSTRAINT ck_import_operation_items_decision,
    DROP CONSTRAINT ck_import_operation_items_action,
    ADD CONSTRAINT ck_import_operation_items_source_row CHECK (
        (action = 'RETIRE' AND source_row_number IS NULL)
        OR
        (action <> 'RETIRE' AND source_row_number > 0)
    ),
    ADD CONSTRAINT ck_import_operation_items_decision CHECK (
        decision IN (
            'NEW', 'EXISTING_UNCHANGED', 'EXISTING_CHANGED', 'RETIRED', 'AMBIGUOUS',
            'CONFLICT', 'INVALID', 'DUPLICATE_IN_FILE', 'OUT_OF_SCOPE'
        )
    ),
    ADD CONSTRAINT ck_import_operation_items_action CHECK (
        action IN ('INSERT', 'UPDATE', 'CREATE_RESULT', 'RETIRE', 'SKIP', 'BLOCKED')
    );

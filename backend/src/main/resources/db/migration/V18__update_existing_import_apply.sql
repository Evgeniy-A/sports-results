ALTER TABLE import_operations
    ADD COLUMN updated_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN result_created_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN new_skipped_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unchanged_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_import_operations_update_counts CHECK (
        updated_count >= 0
        AND result_created_count >= 0
        AND new_skipped_count >= 0
        AND unchanged_count >= 0
        AND result_created_count <= updated_count
    );

UPDATE import_operations operation
SET unchanged_count = (
        SELECT count(*)
        FROM import_operation_items item
        WHERE item.operation_id = operation.id
          AND item.decision = 'EXISTING_UNCHANGED'
    ),
    new_skipped_count = (
        SELECT count(*)
        FROM import_operation_items item
        WHERE item.operation_id = operation.id
          AND item.decision = 'NEW'
          AND item.action = 'SKIP'
    )
WHERE operation.status = 'APPLIED';

ALTER TABLE import_operation_items
    DROP CONSTRAINT ck_import_operation_items_action,
    ADD CONSTRAINT ck_import_operation_items_action CHECK (
        action IN ('INSERT', 'UPDATE', 'CREATE_RESULT', 'SKIP', 'BLOCKED')
    );

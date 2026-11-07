CREATE TABLE result_issue_work_items (
    id BIGSERIAL PRIMARY KEY,
    issue_request_id BIGINT NOT NULL,
    result_id BIGINT,
    base_result_status VARCHAR(64),
    base_gun_time_ms BIGINT,
    base_chip_time_ms BIGINT,
    desired_result_status VARCHAR(64),
    desired_gun_time_ms BIGINT,
    desired_chip_time_ms BIGINT,
    assignee_user_id BIGINT,
    internal_comment TEXT,
    sync_status VARCHAR(16) NOT NULL,
    sync_error_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(160) NOT NULL,
    synced_at TIMESTAMPTZ,
    synced_by VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_result_issue_work_items_issue UNIQUE (issue_request_id),
    CONSTRAINT fk_result_issue_work_items_issue
        FOREIGN KEY (issue_request_id) REFERENCES result_issue_requests(id),
    CONSTRAINT fk_result_issue_work_items_result
        FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE SET NULL,
    CONSTRAINT fk_result_issue_work_items_assignee
        FOREIGN KEY (assignee_user_id) REFERENCES admin_users(id) ON DELETE SET NULL,
    CONSTRAINT ck_result_issue_work_items_status
        CHECK (sync_status IN ('PENDING', 'SYNCED', 'CONFLICT', 'FAILED')),
    CONSTRAINT ck_result_issue_work_items_comment
        CHECK (internal_comment IS NULL OR char_length(internal_comment) <= 4000),
    CONSTRAINT ck_result_issue_work_items_base_gun
        CHECK (base_gun_time_ms IS NULL OR base_gun_time_ms BETWEEN 0 AND 2592000000),
    CONSTRAINT ck_result_issue_work_items_base_chip
        CHECK (base_chip_time_ms IS NULL OR base_chip_time_ms BETWEEN 0 AND 2592000000),
    CONSTRAINT ck_result_issue_work_items_desired_gun
        CHECK (desired_gun_time_ms IS NULL OR desired_gun_time_ms BETWEEN 0 AND 2592000000),
    CONSTRAINT ck_result_issue_work_items_desired_chip
        CHECK (desired_chip_time_ms IS NULL OR desired_chip_time_ms BETWEEN 0 AND 2592000000)
);

CREATE INDEX ix_result_issue_work_items_pending
    ON result_issue_work_items (sync_status, updated_at, id);

CREATE INDEX ix_result_issue_work_items_result
    ON result_issue_work_items (result_id, id);

CREATE INDEX ix_result_issue_work_items_assignee
    ON result_issue_work_items (assignee_user_id, id);

ALTER TABLE admin_change_logs DROP CONSTRAINT ck_admin_change_logs_entity_type;
ALTER TABLE admin_change_logs ADD CONSTRAINT ck_admin_change_logs_entity_type CHECK (
    entity_type IN (
        'REGISTRATION', 'RESULT', 'AWARD_POLICY', 'EVENT_SERIES', 'EVENT',
        'SPORT_FORMAT', 'RACE', 'CATEGORY', 'EVENT_PARTICIPANT_INFO',
        'EVENT_INFO_BLOCK', 'EVENT_SCHEDULE_ITEM', 'EVENT_DOCUMENT',
        'START_CLUSTER', 'ADMIN_USER', 'CERTIFICATE_SETTINGS', 'CERTIFICATE_TEMPLATE',
        'RESULT_ISSUE_WORK_ITEM'
    )
);

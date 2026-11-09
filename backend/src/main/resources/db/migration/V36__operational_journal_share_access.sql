CREATE TABLE result_issue_journal_share_grants (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    event_series_id BIGINT NOT NULL,
    sheet_year INTEGER NOT NULL,
    city_scope VARCHAR(255),
    permission VARCHAR(16) NOT NULL,
    can_sync_now BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(160),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(160),
    last_accessed_at TIMESTAMPTZ,
    access_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_result_issue_journal_share_series
        FOREIGN KEY (event_series_id) REFERENCES event_series(id),
    CONSTRAINT uk_result_issue_journal_share_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_result_issue_journal_share_year CHECK (sheet_year BETWEEN 2000 AND 2200),
    CONSTRAINT ck_result_issue_journal_share_permission CHECK (permission IN ('VIEW', 'EDIT')),
    CONSTRAINT ck_result_issue_journal_share_sync_permission
        CHECK (NOT can_sync_now OR permission = 'EDIT'),
    CONSTRAINT ck_result_issue_journal_share_expiry
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT ck_result_issue_journal_share_access_count CHECK (access_count >= 0),
    CONSTRAINT ck_result_issue_journal_share_revocation
        CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
            OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL))
);

CREATE INDEX ix_result_issue_journal_share_scope
    ON result_issue_journal_share_grants (event_series_id, sheet_year, city_scope);

CREATE INDEX ix_result_issue_journal_share_active_expiry
    ON result_issue_journal_share_grants (expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE result_issue_journal_share_sessions (
    id UUID PRIMARY KEY,
    grant_id UUID NOT NULL,
    session_token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_result_issue_journal_session_grant
        FOREIGN KEY (grant_id) REFERENCES result_issue_journal_share_grants(id),
    CONSTRAINT uk_result_issue_journal_session_token_hash UNIQUE (session_token_hash),
    CONSTRAINT ck_result_issue_journal_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_result_issue_journal_sessions_grant
    ON result_issue_journal_share_sessions (grant_id, expires_at);

CREATE INDEX ix_result_issue_journal_sessions_expiry
    ON result_issue_journal_share_sessions (expires_at);

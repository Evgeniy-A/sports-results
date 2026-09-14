CREATE TABLE result_issue_share_batches (
    id UUID PRIMARY KEY,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(160),
    matched_issue_count BIGINT NOT NULL,
    grant_count BIGINT NOT NULL,
    CONSTRAINT ck_result_issue_share_batch_counts
        CHECK (matched_issue_count >= 0 AND grant_count >= 0),
    CONSTRAINT ck_result_issue_share_batch_revocation
        CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
            OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL))
);

CREATE TABLE result_issue_attachment_share_grants (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    attachment_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(160),
    last_accessed_at TIMESTAMPTZ,
    access_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_result_issue_share_grant_batch
        FOREIGN KEY (batch_id) REFERENCES result_issue_share_batches(id),
    CONSTRAINT fk_result_issue_share_grant_attachment
        FOREIGN KEY (attachment_id) REFERENCES result_issue_attachments(id),
    CONSTRAINT uk_result_issue_share_grant_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_result_issue_share_grant_batch_attachment UNIQUE (batch_id, attachment_id),
    CONSTRAINT ck_result_issue_share_grant_access_count CHECK (access_count >= 0),
    CONSTRAINT ck_result_issue_share_grant_revocation
        CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
            OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL))
);

CREATE INDEX ix_result_issue_share_grants_attachment
    ON result_issue_attachment_share_grants(attachment_id);

CREATE INDEX ix_result_issue_share_grants_expires_at
    ON result_issue_attachment_share_grants(expires_at)
    WHERE expires_at IS NOT NULL AND revoked_at IS NULL;

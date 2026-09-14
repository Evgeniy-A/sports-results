-- Global journal access paths. Historical filters intentionally target snapshot columns.
CREATE INDEX ix_result_issue_journal_created
    ON result_issue_requests(created_at DESC, id DESC);

CREATE INDEX ix_result_issue_journal_event_created
    ON result_issue_requests(event_id, created_at DESC, id DESC);

CREATE INDEX ix_result_issue_journal_bib_created
    ON result_issue_requests(snapshot_bib, created_at DESC, id DESC)
    WHERE snapshot_bib IS NOT NULL;

CREATE INDEX ix_result_issue_journal_race_created
    ON result_issue_requests(snapshot_race_id, created_at DESC, id DESC)
    WHERE snapshot_race_id IS NOT NULL;

CREATE INDEX ix_result_issue_journal_event_date_created
    ON result_issue_requests(snapshot_event_starts_at, created_at DESC, id DESC)
    WHERE snapshot_event_starts_at IS NOT NULL;

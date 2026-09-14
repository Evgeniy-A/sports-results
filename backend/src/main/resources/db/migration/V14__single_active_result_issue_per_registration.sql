DO $$
DECLARE
    conflicting_registrations TEXT;
BEGIN
    SELECT string_agg(registration_id::TEXT || ' (' || active_count || ')', ', ' ORDER BY registration_id)
    INTO conflicting_registrations
    FROM (
        SELECT registration_id, count(*) AS active_count
        FROM result_issue_requests
        WHERE status IN ('NEW', 'IN_PROGRESS')
        GROUP BY registration_id
        HAVING count(*) > 1
    ) conflicts;

    IF conflicting_registrations IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot enforce one active result issue per registration. Conflicts: %',
            conflicting_registrations;
    END IF;
END $$;

DROP INDEX uk_result_issue_active_registration_type;

CREATE UNIQUE INDEX uk_result_issue_active_registration
    ON result_issue_requests(registration_id)
    WHERE status IN ('NEW', 'IN_PROGRESS');

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE registrations ADD COLUMN search_text TEXT GENERATED ALWAYS AS (
    lower(
        coalesce(display_name, '') || ' ' ||
        coalesce(first_name, '') || ' ' ||
        coalesce(last_name, '') || ' ' ||
        coalesce(last_name, '') || ' ' ||
        coalesce(first_name, '')
    )
) STORED;

CREATE INDEX ix_registrations_search_trgm
    ON registrations USING gin (search_text gin_trgm_ops);

CREATE INDEX ix_results_gender_place ON results(gender_place);
CREATE INDEX ix_results_net_gender_place ON results(net_gender_place);
CREATE INDEX ix_results_category_place ON results(category_place);
CREATE INDEX ix_results_net_category_place ON results(net_category_place);

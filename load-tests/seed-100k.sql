-- Run against a disposable database after Flyway has created schema version 3.
-- The fixed load-test slug makes accidental repeated seeding fail fast.
BEGIN;

DO $seed$
DECLARE
    seeded_series_id BIGINT;
    seeded_event_id BIGINT;
    seeded_race_id BIGINT;
    male_category_id BIGINT;
    female_category_id BIGINT;
    seeded_batch_id BIGINT;
BEGIN
    INSERT INTO event_series(name, slug, description, active, created_at, updated_at)
    VALUES ('Нагрузочная серия', 'load-test-series', 'Синтетические данные только для benchmark', TRUE, now(), now())
    RETURNING id INTO seeded_series_id;

    INSERT INTO events(event_series_id, name, slug, starts_at, ends_at, location, publication_status, created_at, updated_at)
    VALUES (seeded_series_id, 'Нагрузочный старт 2027', 'load-test-2027',
            '2027-06-15T06:00:00Z', '2027-06-15T18:00:00Z', 'Екатеринбург', 'PUBLISHED', now(), now())
    RETURNING id INTO seeded_event_id;

    INSERT INTO races(event_id, source_code, name, slug, distance_meters, starts_at, display_order, created_at, updated_at)
    VALUES (seeded_event_id, 'LOAD-10K', '10 km', '10-km', 10000, '2027-06-15T06:00:00Z', 0, now(), now())
    RETURNING id INTO seeded_race_id;

    INSERT INTO categories(race_id, source_name, display_name, display_order, created_at, updated_at)
    VALUES (seeded_race_id, '18+ Male', '18+ Male', 0, now(), now())
    RETURNING id INTO male_category_id;

    INSERT INTO categories(race_id, source_name, display_name, display_order, created_at, updated_at)
    VALUES (seeded_race_id, '18+ Female', '18+ Female', 1, now(), now())
    RETURNING id INTO female_category_id;

    INSERT INTO import_batches(event_id, race_id, scope_type, source_filename, file_sha256, status,
                               total_rows, imported_rows, skipped_rows, failed_rows,
                               started_at, finished_at, created_at, updated_at)
    VALUES (seeded_event_id, NULL, 'EVENT', 'generated-load-test.csv', repeat('a', 64), 'SUCCEEDED',
            100000, 100000, 0, 0, now(), now(), now(), now())
    RETURNING id INTO seeded_batch_id;

    INSERT INTO registrations(race_id, category_id, import_batch_id, entry_kind, bib, display_name,
                              first_name, last_name, birth_date, gender, source_row_number,
                              source_row_hash, created_at, updated_at)
    SELECT seeded_race_id,
           CASE WHEN n % 2 = 0 THEN female_category_id ELSE male_category_id END,
           seeded_batch_id,
           'PERSON',
           lpad(n::text, 6, '0'),
           CASE WHEN n % 10 = 0 THEN 'Alex Runner ' || n ELSE 'Иван Петров ' || n END,
           CASE WHEN n % 10 = 0 THEN 'Alex' ELSE 'Иван' END,
           CASE WHEN n % 10 = 0 THEN 'Runner ' || n ELSE 'Петров ' || n END,
           date '1980-01-01' + (n % 12000),
           CASE WHEN n % 2 = 0 THEN 'female' ELSE 'male' END,
           n,
           md5('load-registration-' || n) || md5('row-' || n),
           now(),
           now()
    FROM generate_series(1, 100000) AS generated(n);

    INSERT INTO results(registration_id, status, gun_time_ms, chip_time_ms,
                        overall_place, gender_place, category_place,
                        net_overall_place, net_gender_place, net_category_place,
                        created_at, updated_at)
    SELECT registration.id,
           CASE
               WHEN registration.source_row_number % 211 = 0 THEN 'disqualified'
               WHEN registration.source_row_number % 100 = 0 THEN 'notstarted'
               ELSE 'finished'
           END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE 1800000 + registration.source_row_number * 13 END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE 1795000 + registration.source_row_number * 13 END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE registration.source_row_number END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE (registration.source_row_number + 1) / 2 END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE (registration.source_row_number + 1) / 2 END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE registration.source_row_number END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE (registration.source_row_number + 1) / 2 END,
           CASE WHEN registration.source_row_number % 211 = 0 OR registration.source_row_number % 100 = 0
                THEN NULL ELSE (registration.source_row_number + 1) / 2 END,
           now(),
           now()
    FROM registrations registration
    WHERE registration.import_batch_id = seeded_batch_id;

    INSERT INTO award_policies(race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                               category_enabled, category_prize_places, exclude_absolute_winners_from_category,
                               created_at, updated_at)
    VALUES (seeded_race_id, 'CHIP_TIME', 'BY_GENDER', 3, TRUE, 3, TRUE, now(), now());

    RAISE NOTICE 'event_id=%, race_id=%, male_category_id=%, female_category_id=%',
        seeded_event_id, seeded_race_id, male_category_id, female_category_id;
END
$seed$;

ANALYZE event_series;
ANALYZE events;
ANALYZE races;
ANALYZE categories;
ANALYZE registrations;
ANALYZE results;

COMMIT;

SELECT event.id AS event_id, race.id AS race_id,
       min(category.id) FILTER (WHERE category.display_name = '18+ Female') AS female_category_id,
       count(DISTINCT registration.id) AS registrations
FROM events event
JOIN races race ON race.event_id = event.id
JOIN categories category ON category.race_id = race.id
JOIN registrations registration ON registration.race_id = race.id
WHERE event.slug = 'load-test-2027'
GROUP BY event.id, race.id;

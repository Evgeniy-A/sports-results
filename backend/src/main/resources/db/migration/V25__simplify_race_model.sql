DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM races race
        LEFT JOIN sport_formats format ON format.id = race.sport_format_id
        WHERE race.sport_format_id IS NULL
           OR format.id IS NULL
           OR format.event_id <> race.event_id
    ) THEN
        RAISE EXCEPTION 'V25 cannot simplify Race: a Race has no valid SportFormat in the same Event';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_operations
        WHERE status IN ('PREVIEWED', 'APPLYING')
    ) THEN
        RAISE EXCEPTION 'V25 cannot simplify Race while PREVIEWED or APPLYING imports exist';
    END IF;
END $$;

CREATE TEMP TABLE v25_race_projection ON COMMIT DROP AS
WITH normalized AS (
    SELECT race.id,
           race.event_id,
           race.public_visible AND format.public_visible AS public_visible,
           row_number() OVER (
               PARTITION BY race.event_id
               ORDER BY format.display_order, race.display_order, race.id
           ) - 1 AS display_order,
           btrim(race.name) AS race_name,
           btrim(format.display_name) AS format_name,
           lower(regexp_replace(btrim(race.name), '[[:space:]]+', ' ', 'g')) AS normalized_race_name,
           lower(regexp_replace(btrim(format.display_name), '[[:space:]]+', ' ', 'g')) AS normalized_format_name
    FROM races race
    JOIN sport_formats format
      ON format.id = race.sport_format_id
     AND format.event_id = race.event_id
), projected AS (
    SELECT id,
           event_id,
           public_visible,
           display_order,
           CASE
               WHEN normalized_format_name IN ('основной формат', 'основной', 'default')
                 OR normalized_race_name = normalized_format_name
                 OR (
                     left(normalized_race_name, char_length(normalized_format_name)) = normalized_format_name
                     AND substring(
                         normalized_race_name
                         FROM char_length(normalized_format_name) + 1
                         FOR 1
                     ) IN (' ', '-', '—', '/', ':', '·', ',', '(')
                 )
               THEN race_name
               ELSE format_name || ' ' || race_name
           END AS name
    FROM normalized
)
SELECT id, event_id, name, public_visible, display_order
FROM projected;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM v25_race_projection
        WHERE name IS NULL OR btrim(name) = ''
    ) THEN
        RAISE EXCEPTION 'V25 projected an empty Race name';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v25_race_projection
        WHERE char_length(name) > 255
    ) THEN
        RAISE EXCEPTION 'V25 projected a Race name longer than 255 characters';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v25_race_projection
        GROUP BY event_id, lower(name)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V25 projected duplicate case-insensitive Race names inside one Event';
    END IF;
END $$;

UPDATE races race
SET name = projection.name,
    public_visible = projection.public_visible,
    display_order = projection.display_order
FROM v25_race_projection projection
WHERE projection.id = race.id;

DROP INDEX ix_races_sport_format_order;

ALTER TABLE races
    DROP CONSTRAINT fk_races_sport_format_event,
    DROP CONSTRAINT ck_races_entry_mode,
    DROP COLUMN sport_format_id,
    DROP COLUMN entry_mode;

DROP TABLE sport_formats;

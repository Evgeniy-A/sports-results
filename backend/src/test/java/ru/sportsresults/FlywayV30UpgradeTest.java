package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV30UpgradeTest {

    @Test
    void backfillsExistingDocumentsAsLocalWithoutChangingMetadata() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("29").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v30', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Event', 'event-v30', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long documentId = jdbc.queryForObject("""
                    INSERT INTO event_documents(
                        event_id, type, display_name, original_filename, storage_key,
                        content_type, size_bytes, sha256, display_order, is_public,
                        uploaded_at, uploaded_by, created_at, updated_at
                    ) VALUES (
                        ?, 'PARTICIPANT_GUIDE', 'Guide', 'guide.pdf',
                        '550e8400-e29b-41d4-a716-446655440000.pdf', 'application/pdf', 123,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        4, false, now(), 'tester', now(), now()
                    ) RETURNING id
                    """, Long.class, eventId);
            Map<String, Object> before = jdbc.queryForMap("""
                    SELECT storage_key, sha256, size_bytes, display_name, original_filename,
                           content_type, display_order, is_public, uploaded_by
                    FROM event_documents WHERE id=?
                    """, documentId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("30").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("30");
            assertThat(jdbc.queryForObject(
                    "SELECT storage_provider FROM event_documents WHERE id=?", String.class, documentId
            )).isEqualTo("LOCAL");
            assertThat(jdbc.queryForMap("""
                    SELECT storage_key, sha256, size_bytes, display_name, original_filename,
                           content_type, display_order, is_public, uploaded_by
                    FROM event_documents WHERE id=?
                    """, documentId)).isEqualTo(before);
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE event_documents SET storage_provider='S3' WHERE id=?", documentId
            )).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase()).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("30");
        }
    }
}

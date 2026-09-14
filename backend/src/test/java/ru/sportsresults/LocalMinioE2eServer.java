package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit optional E2E server for real local MinIO. It is excluded from normal test discovery.
 */
class LocalMinioE2eServer {

    @Test
    void serveIsolatedFixturesAgainstLocalMinioUntilStopMarkerAppears() throws Exception {
        String fakeScanResult = System.getProperty("local.minio.fake-scan-result", "CLEAN");
        Path stopMarker = Path.of("target", "local-minio-e2e.stop").toAbsolutePath().normalize();
        Files.deleteIfExists(stopMarker);
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            String jdbcUrl;
            String username;
            try (Connection connection = dataSource.getConnection()) {
                jdbcUrl = connection.getMetaData().getURL();
                username = connection.getMetaData().getUserName();
            }
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    SportsResultsApplication.class
            )
                    .web(WebApplicationType.SERVLET)
                    .run(
                            "--server.port=8082",
                            "--spring.profiles.active=local",
                            "--spring.datasource.url=" + jdbcUrl,
                            "--spring.datasource.username=" + username,
                            "--spring.datasource.password=",
                            "--app.admin.username=e2e-admin",
                            "--app.admin.password=e2e-secret",
                            "--app.documents.storage-root="
                                    + Path.of("target", "local-minio-documents").toAbsolutePath(),
                            "--app.result-issues.attachments.storage-provider=s3",
                            "--app.result-issues.attachments.s3.endpoint=http://127.0.0.1:9000",
                            "--app.result-issues.attachments.s3.region=us-east-1",
                            "--app.result-issues.attachments.s3.bucket=result-issue-files",
                            "--app.result-issues.attachments.s3.access-key=local-sports-results",
                            "--app.result-issues.attachments.s3.secret-key=local-sports-results-secret",
                            "--app.result-issues.attachments.s3.path-style-access=true",
                            "--app.result-issues.attachments.scanner-provider=fake",
                            "--app.result-issues.attachments.fake-scanner.result=" + fakeScanResult,
                            "--logging.level.root=WARN"
                    )) {
                BrowserE2eServer.seed(context);
                System.out.println("LOCAL_MINIO_E2E_READY http://127.0.0.1:8082 " + fakeScanResult);
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
                while (!Files.exists(stopMarker) && System.nanoTime() < deadline) {
                    Thread.sleep(250);
                }
                assertThat(Files.exists(stopMarker)).as("local MinIO E2E stop marker").isTrue();
            } finally {
                Files.deleteIfExists(stopMarker);
            }
        }
    }
}

package ru.sportsresults;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;
import ru.sportsresults.storage.attachments.PreparedObjectDownload;
import ru.sportsresults.storage.attachments.PreparedObjectUpload;
import ru.sportsresults.storage.attachments.StoredObjectMetadata;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration(proxyBeanMethods = false)
class BrowserE2eAttachmentStorageConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    BrowserE2eAttachmentObjectStorage browserE2eAttachmentObjectStorage() throws IOException {
        return new BrowserE2eAttachmentObjectStorage();
    }

    static final class BrowserE2eAttachmentObjectStorage implements AttachmentObjectStorage, AutoCloseable {

        private final Map<String, StoredObjectMetadata> objects = new ConcurrentHashMap<>();
        private final HttpServer server;
        private final URI endpoint;

        BrowserE2eAttachmentObjectStorage() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/objects", this::handleObjectRequest);
            server.start();
            endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public boolean supportsDirectBrowserUpload() {
            return true;
        }

        @Override
        public PreparedObjectUpload prepareUpload(
                String storageKey,
                String declaredContentType,
                long declaredSizeBytes,
                Duration lifetime
        ) {
            return new PreparedObjectUpload(
                    objectUri(storageKey),
                    "PUT",
                    declaredContentType == null ? Map.of() : Map.of("Content-Type", declaredContentType),
                    Instant.now().plus(lifetime)
            );
        }

        @Override
        public Optional<StoredObjectMetadata> findObject(String storageKey) {
            return Optional.ofNullable(objects.get(storageKey));
        }

        @Override
        public PreparedObjectDownload prepareDownload(String storageKey, Duration lifetime) {
            return new PreparedObjectDownload(objectUri(storageKey), Instant.now().plus(lifetime));
        }

        @Override
        public void deleteObject(String storageKey) {
            objects.remove(storageKey);
        }

        @Override
        public void close() {
            server.stop(0);
            objects.clear();
        }

        private URI objectUri(String storageKey) {
            return endpoint.resolve("/objects/" + URLEncoder.encode(storageKey, StandardCharsets.UTF_8)
                    + "?signature=" + UUID.randomUUID());
        }

        private void handleObjectRequest(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!exchange.getRequestMethod().equals("PUT")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String encodedKey = path.substring("/objects/".length());
            String storageKey = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            byte[] content = exchange.getRequestBody().readAllBytes();
            objects.put(storageKey, new StoredObjectMetadata(
                    content.length,
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    "e2e-" + Integer.toHexString(java.util.Arrays.hashCode(content))
            ));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }

        private static void addCorsHeaders(HttpExchange exchange) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                exchange.getResponseHeaders().set("Vary", "Origin");
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "PUT, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }
    }
}

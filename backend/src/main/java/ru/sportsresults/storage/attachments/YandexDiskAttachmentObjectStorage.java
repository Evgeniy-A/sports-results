package ru.sportsresults.storage.attachments;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.sportsresults.config.YandexDiskAttachmentStorageProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class YandexDiskAttachmentObjectStorage implements AttachmentObjectStorage {

    private static final String EXISTING_DIRECTORY_ERROR = "DiskPathPointsToExistentDirectoryError";

    private final RestClient restClient;
    private final YandexDiskAttachmentStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public YandexDiskAttachmentObjectStorage(
            RestClient restClient,
            YandexDiskAttachmentStorageProperties properties
    ) {
        this(restClient, properties, new ObjectMapper(), Clock.systemUTC());
    }

    YandexDiskAttachmentObjectStorage(
            RestClient restClient,
            YandexDiskAttachmentStorageProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        String path = objectPath(storageKey);
        ensureParentDirectories(storageKey);
        JsonNode link = getJson(apiUri("/resources/upload", Map.of(
                "path", path,
                "overwrite", "true"
        )), "prepare upload");
        String method = requiredText(link, "method", "prepare upload");
        Map<String, String> headers = declaredContentType == null || declaredContentType.isBlank()
                ? Map.of()
                : Map.of("Content-Type", declaredContentType);
        return new PreparedObjectUpload(
                requiredUri(link, "href", "prepare upload"),
                method,
                headers,
                Instant.now(clock).plus(lifetime)
        );
    }

    @Override
    public Optional<StoredObjectMetadata> findObject(String storageKey) {
        URI uri = apiUri("/resources", Map.of(
                "path", objectPath(storageKey),
                "fields", "size,mime_type,md5,sha256"
        ));
        try {
            JsonNode resource = readJson(restClient.get().uri(uri).retrieve().body(String.class), "read metadata");
            JsonNode size = resource.get("size");
            if (size == null || !size.canConvertToLong()) {
                throw new IllegalStateException("Yandex Disk metadata response has no valid size");
            }
            String contentType = optionalText(resource, "mime_type");
            String digest = optionalText(resource, "md5");
            if (digest == null) {
                digest = optionalText(resource, "sha256");
            }
            return Optional.of(new StoredObjectMetadata(size.asLong(), contentType, digest));
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw apiFailure("read metadata", exception);
        } catch (RestClientException exception) {
            throw transportFailure("read metadata", exception);
        }
    }

    @Override
    public PreparedObjectDownload prepareDownload(String storageKey, Duration lifetime) {
        JsonNode link = getJson(apiUri("/resources/download", Map.of(
                "path", objectPath(storageKey)
        )), "prepare download");
        return new PreparedObjectDownload(
                requiredUri(link, "href", "prepare download"),
                Instant.now(clock).plus(lifetime)
        );
    }

    @Override
    public void deleteObject(String storageKey) {
        URI uri = apiUri("/resources", Map.of(
                "path", objectPath(storageKey),
                "permanently", "true"
        ));
        try {
            restClient.delete().uri(uri).retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw apiFailure("delete object", exception);
            }
        } catch (RestClientException exception) {
            throw transportFailure("delete object", exception);
        }
    }

    private void ensureParentDirectories(String storageKey) {
        String[] segments = normalizedKey(storageKey).split("/");
        String current = normalizedRoot();
        for (int index = 0; index < segments.length - 1; index++) {
            current += "/" + segments[index];
            createDirectoryIfMissing(current);
        }
    }

    private void createDirectoryIfMissing(String path) {
        try {
            restClient.put()
                    .uri(apiUri("/resources", Map.of("path", path)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()
                    && EXISTING_DIRECTORY_ERROR.equals(apiErrorCode(exception))) {
                return;
            }
            throw apiFailure("create directory", exception);
        } catch (RestClientException exception) {
            throw transportFailure("create directory", exception);
        }
    }

    private JsonNode getJson(URI uri, String operation) {
        try {
            return readJson(restClient.get().uri(uri).retrieve().body(String.class), operation);
        } catch (HttpStatusCodeException exception) {
            throw apiFailure(operation, exception);
        } catch (RestClientException exception) {
            throw transportFailure(operation, exception);
        }
    }

    private JsonNode readJson(String body, String operation) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Yandex Disk " + operation + " returned an empty response");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Yandex Disk " + operation + " returned invalid JSON", exception);
        }
    }

    private URI apiUri(String endpoint, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(properties.apiBaseUrl()).path(endpoint);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private String objectPath(String storageKey) {
        return normalizedRoot() + "/" + normalizedKey(storageKey);
    }

    private String normalizedRoot() {
        String root = properties.rootPath().strip().replace('\\', '/');
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (!root.equals("app:")) {
            throw new IllegalStateException("Yandex Disk attachment root must be app:/");
        }
        return root;
    }

    private static String normalizedKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        String normalized = storageKey.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.endsWith("/")) {
            throw new IllegalArgumentException("Storage key must be relative");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Storage key contains an invalid path segment");
            }
        }
        return normalized;
    }

    private URI requiredUri(JsonNode node, String field, String operation) {
        String value = requiredText(node, field, operation);
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Yandex Disk " + operation + " returned an invalid " + field, exception);
        }
    }

    private static String requiredText(JsonNode node, String field, String operation) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalStateException("Yandex Disk " + operation + " response has no " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private IllegalStateException apiFailure(String operation, HttpStatusCodeException exception) {
        String code = apiErrorCode(exception);
        String suffix = code == null ? "" : " (" + code + ")";
        return new IllegalStateException(
                "Yandex Disk " + operation + " failed with HTTP " + exception.getStatusCode().value() + suffix,
                exception
        );
    }

    private String apiErrorCode(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return optionalText(objectMapper.readTree(body), "error");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static IllegalStateException transportFailure(String operation, RestClientException exception) {
        return new IllegalStateException("Yandex Disk " + operation + " request failed", exception);
    }
}

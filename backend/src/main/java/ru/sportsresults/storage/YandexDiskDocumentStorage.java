package ru.sportsresults.storage;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.sportsresults.config.YandexDiskDocumentStorageProperties;
import ru.sportsresults.domain.EventDocumentStorageProvider;
import ru.sportsresults.service.ResourceNotFoundException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

public class YandexDiskDocumentStorage implements DocumentStorage {
    private static final String EXISTING_DIRECTORY_ERROR = "DiskPathPointsToExistentDirectoryError";

    private final RestClient apiRestClient;
    private final RestClient transferRestClient;
    private final YandexDiskDocumentStorageProperties properties;
    private final ObjectMapper objectMapper;

    public YandexDiskDocumentStorage(
            RestClient apiRestClient,
            RestClient transferRestClient,
            YandexDiskDocumentStorageProperties properties
    ) {
        this(apiRestClient, transferRestClient, properties, new ObjectMapper());
    }

    YandexDiskDocumentStorage(
            RestClient apiRestClient,
            RestClient transferRestClient,
            YandexDiskDocumentStorageProperties properties,
            ObjectMapper objectMapper
    ) {
        this.apiRestClient = apiRestClient;
        this.transferRestClient = transferRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EventDocumentStorageProvider provider() {
        return EventDocumentStorageProvider.YANDEX_DISK;
    }

    @Override
    public void validateConfiguration() {
        normalizedRoot();
        if (properties.token() == null || properties.token().isBlank()) {
            throw new IllegalStateException("Yandex Disk document storage token must not be blank");
        }
    }

    @Override
    public String store(Long eventId, byte[] content) {
        validateConfiguration();
        String storageKey = "events/" + eventId + "/documents/" + UUID.randomUUID() + ".pdf";
        String safeKey = DocumentStorageKey.requireSafeRelative(storageKey);
        ensureParentDirectories(safeKey);
        JsonNode upload = getJson(apiUri("/resources/upload", Map.of(
                "path", objectPath(safeKey),
                "overwrite", "false"
        )), "prepare upload");
        URI uploadUrl = requiredUri(upload, "href", "prepare upload");
        try {
            transferRestClient.put()
                    .uri(uploadUrl)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            return safeKey;
        } catch (RestClientException exception) {
            throw transportFailure("upload document", exception);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        validateConfiguration();
        JsonNode download = getJson(apiUri("/resources/download", Map.of(
                "path", objectPath(storageKey)
        )), "prepare download");
        URI downloadUrl = requiredUri(download, "href", "prepare download");
        try {
            byte[] content = transferRestClient.get().uri(downloadUrl).retrieve().body(byte[].class);
            if (content == null) {
                throw new IllegalStateException("Yandex Disk document download returned an empty response");
            }
            return new ByteArrayInputStream(content);
        } catch (RestClientException exception) {
            throw transportFailure("download document", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        validateConfiguration();
        URI uri = apiUri("/resources", Map.of(
                "path", objectPath(storageKey),
                "permanently", "true"
        ));
        try {
            apiRestClient.delete().uri(uri).retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw apiFailure("delete document", exception);
            }
        } catch (RestClientException exception) {
            throw transportFailure("delete document", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        validateConfiguration();
        URI uri = apiUri("/resources", Map.of(
                "path", objectPath(storageKey),
                "fields", "path"
        ));
        try {
            apiRestClient.get().uri(uri).retrieve().toBodilessEntity();
            return true;
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw apiFailure("check document", exception);
        } catch (RestClientException exception) {
            throw transportFailure("check document", exception);
        }
    }

    private void ensureParentDirectories(String storageKey) {
        String[] segments = DocumentStorageKey.requireSafeRelative(storageKey).split("/");
        String current = normalizedRoot();
        for (int index = 0; index < segments.length - 1; index++) {
            current += "/" + segments[index];
            createDirectoryIfMissing(current);
        }
    }

    private void createDirectoryIfMissing(String path) {
        try {
            apiRestClient.put()
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
            return readJson(apiRestClient.get().uri(uri).retrieve().body(String.class), operation);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    && operation.equals("prepare download")) {
                throw new ResourceNotFoundException("DOCUMENT_CONTENT_NOT_FOUND", "Document content not found");
            }
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
        return normalizedRoot() + "/" + DocumentStorageKey.requireSafeRelative(storageKey);
    }

    private String normalizedRoot() {
        String root = properties.rootPath().strip().replace('\\', '/');
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (!root.equals("app:")) {
            throw new IllegalStateException("Yandex Disk document root must be app:/");
        }
        return root;
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
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("Yandex Disk " + operation + " response has no " + field);
        }
        return value.asText();
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
            JsonNode value = objectMapper.readTree(body).get("error");
            return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static IllegalStateException transportFailure(String operation, RestClientException exception) {
        return new IllegalStateException("Yandex Disk " + operation + " request failed", exception);
    }
}

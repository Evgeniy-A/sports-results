package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventDocumentStorageProvider;
import ru.sportsresults.domain.EventDocumentType;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.EventDocumentRepository;
import ru.sportsresults.storage.FileStorageService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventDocumentServiceStorageTest {
    private static final byte[] PDF = "%PDF-1.4\nsynthetic".getBytes(StandardCharsets.US_ASCII);

    @Test
    void deletesTheNewObjectWithItsActualProviderWhenDatabasePersistenceFails() {
        EventService eventService = mock(EventService.class);
        EventDocumentRepository repository = mock(EventDocumentRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminChangeLogRepository auditRepository = mock(AdminChangeLogRepository.class);
        EventDocumentService service = new EventDocumentService(
                eventService, repository, storage, auditRepository, 10_485_760L
        );
        when(eventService.requireEvent(7L)).thenReturn(new Event());
        when(storage.store(7L, PDF)).thenReturn(new FileStorageService.StoredDocument(
                EventDocumentStorageProvider.YANDEX_DISK,
                "events/7/documents/test.pdf"
        ));
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.upload(
                7L, EventDocumentType.PARTICIPANT_GUIDE, "Guide", 0, true,
                "guide.pdf", "application/pdf", PDF, "tester"
        )).isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");

        verify(storage).delete(
                EventDocumentStorageProvider.YANDEX_DISK,
                "events/7/documents/test.pdf"
        );
        verify(auditRepository, never()).save(any());
    }

    @Test
    void documentListsUseOnlyDatabaseMetadataAndNeverContactStorage() {
        EventService eventService = mock(EventService.class);
        EventDocumentRepository repository = mock(EventDocumentRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        EventDocumentService service = new EventDocumentService(
                eventService, repository, storage, mock(AdminChangeLogRepository.class), 10_485_760L
        );
        when(eventService.requireEvent(7L)).thenReturn(new Event());
        when(repository.findAllByEventIdOrderByDisplayOrderAscIdAsc(7L)).thenReturn(List.of());

        service.listAdmin(7L);

        verifyNoInteractions(storage);
    }
}

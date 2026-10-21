ALTER TABLE event_documents
    ADD COLUMN storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE event_documents
    ADD CONSTRAINT ck_event_documents_storage_provider
        CHECK (storage_provider IN ('LOCAL', 'YANDEX_DISK'));

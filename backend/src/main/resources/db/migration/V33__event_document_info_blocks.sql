ALTER TABLE event_documents
    ADD COLUMN info_block_id BIGINT NULL;

ALTER TABLE event_documents
    ADD CONSTRAINT fk_event_documents_info_block
        FOREIGN KEY (info_block_id) REFERENCES event_info_blocks(id) ON DELETE SET NULL;

CREATE INDEX idx_event_documents_info_block
    ON event_documents(info_block_id, display_order, id);

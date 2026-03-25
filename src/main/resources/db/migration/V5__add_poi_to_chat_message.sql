ALTER TABLE chat_message
    ADD COLUMN poi_id BIGINT NULL;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_poi
        FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE SET NULL;

CREATE INDEX idx_chat_message_poi_id
    ON chat_message(poi_id);

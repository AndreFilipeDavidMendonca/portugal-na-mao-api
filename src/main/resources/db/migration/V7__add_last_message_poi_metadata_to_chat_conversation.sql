ALTER TABLE chat_conversation
    ADD COLUMN last_message_type VARCHAR(20) NULL,
    ADD COLUMN last_message_body VARCHAR(4000) NULL,
    ADD COLUMN last_poi_id BIGINT NULL,
    ADD COLUMN last_poi_name VARCHAR(300) NULL,
    ADD COLUMN last_poi_image VARCHAR(2000) NULL,
    ADD COLUMN last_message_at TIMESTAMPTZ NULL;

ALTER TABLE chat_conversation
    ADD CONSTRAINT fk_chat_conversation_last_poi
        FOREIGN KEY (last_poi_id) REFERENCES poi(id) ON DELETE SET NULL;

CREATE INDEX idx_chat_conversation_last_message_at
    ON chat_conversation(last_message_at);

CREATE INDEX idx_chat_conversation_last_poi_id
    ON chat_conversation(last_poi_id);

UPDATE chat_conversation c
SET last_message_type = lm.type,
    last_message_body = lm.body,
    last_poi_id = lm.poi_id,
    last_poi_name = lm.poi_name,
    last_poi_image = lm.poi_image,
    last_message_at = lm.created_at,
    updated_at = GREATEST(c.updated_at, lm.created_at)
FROM (
    SELECT DISTINCT ON (conversation_id)
           conversation_id,
           type,
           body,
           poi_id,
           poi_name,
           poi_image,
           created_at
    FROM chat_message
    ORDER BY conversation_id, created_at DESC, id DESC
) lm
WHERE c.id = lm.conversation_id;

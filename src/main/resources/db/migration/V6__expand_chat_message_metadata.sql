ALTER TABLE chat_message
    ADD COLUMN receiver_id UUID NULL,
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN poi_name VARCHAR(300) NULL,
    ADD COLUMN poi_image VARCHAR(2000) NULL,
    ADD COLUMN read_at TIMESTAMPTZ NULL;

UPDATE chat_message m
SET receiver_id = CASE
    WHEN c.user_a_id = m.sender_id THEN c.user_b_id
    ELSE c.user_a_id
END
FROM chat_conversation c
WHERE c.id = m.conversation_id
  AND m.receiver_id IS NULL;

ALTER TABLE chat_message
    ALTER COLUMN receiver_id SET NOT NULL;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_receiver
        FOREIGN KEY (receiver_id) REFERENCES app_user(id) ON DELETE CASCADE;

CREATE INDEX idx_chat_message_receiver_id
    ON chat_message(receiver_id);

CREATE INDEX idx_chat_message_receiver_id_read_at
    ON chat_message(receiver_id, read_at);

CREATE INDEX idx_chat_message_type
    ON chat_message(type);

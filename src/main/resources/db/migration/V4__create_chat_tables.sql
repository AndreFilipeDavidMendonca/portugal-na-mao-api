CREATE TABLE chat_conversation (
                                   id UUID PRIMARY KEY,
                                   user_a_id UUID NOT NULL,
                                   user_b_id UUID NOT NULL,
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                   updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                   CONSTRAINT uk_chat_conversation_user_a_user_b
                                       UNIQUE (user_a_id, user_b_id),

                                   CONSTRAINT fk_chat_conversation_user_a
                                       FOREIGN KEY (user_a_id) REFERENCES app_user(id) ON DELETE CASCADE,

                                   CONSTRAINT fk_chat_conversation_user_b
                                       FOREIGN KEY (user_b_id) REFERENCES app_user(id) ON DELETE CASCADE,

                                   CONSTRAINT chk_chat_conversation_distinct_users
                                       CHECK (user_a_id <> user_b_id)
);

CREATE TABLE chat_message (
                              id UUID PRIMARY KEY,
                              conversation_id UUID NOT NULL,
                              sender_id UUID NOT NULL,
                              body VARCHAR(4000) NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                              CONSTRAINT fk_chat_message_conversation
                                  FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE,

                              CONSTRAINT fk_chat_message_sender
                                  FOREIGN KEY (sender_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_conversation_user_a_id
    ON chat_conversation(user_a_id);

CREATE INDEX idx_chat_conversation_user_b_id
    ON chat_conversation(user_b_id);

CREATE INDEX idx_chat_message_conversation_id_created_at
    ON chat_message(conversation_id, created_at);

CREATE INDEX idx_chat_message_sender_id
    ON chat_message(sender_id);

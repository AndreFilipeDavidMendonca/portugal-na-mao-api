cat > src/main/resources/db/migration/V8__make_chat_message_body_nullable.sql <<'SQL'
ALTER TABLE chat_message
    ALTER COLUMN body DROP NOT NULL;
SQL
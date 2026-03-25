CREATE INDEX idx_poi_district_id
    ON poi(district_id);

CREATE INDEX idx_poi_category
    ON poi(category);

CREATE INDEX idx_favorite_user_id
    ON favorite(user_id);

CREATE INDEX idx_chat_conversation_user_a_id
    ON chat_conversation(user_a_id);

CREATE INDEX idx_chat_conversation_user_b_id
    ON chat_conversation(user_b_id);

CREATE INDEX idx_chat_message_conversation_id_created_at
    ON chat_message(conversation_id, created_at);

CREATE INDEX idx_chat_message_sender_id
    ON chat_message(sender_id);

CREATE INDEX idx_chat_message_receiver_id
    ON chat_message(receiver_id);

CREATE INDEX idx_chat_message_receiver_id_read_at
    ON chat_message(receiver_id, read_at);

CREATE INDEX idx_chat_message_poi_id
    ON chat_message(poi_id);

CREATE INDEX idx_chat_message_type
    ON chat_message(type);

CREATE INDEX idx_poi_comment_poi_id
    ON poi_comment(poi_id);

CREATE INDEX idx_poi_comment_user_id
    ON poi_comment(user_id);

CREATE INDEX idx_media_item_poi_id
    ON media_item(poi_id);

CREATE INDEX idx_media_item_district_id
    ON media_item(district_id);

CREATE INDEX idx_friendship_requester_id
    ON friendship(requester_id);

CREATE INDEX idx_friendship_receiver_id
    ON friendship(receiver_id);

CREATE INDEX idx_friendship_status
    ON friendship(status);

CREATE INDEX idx_poi_image_poi_id
    ON poi_image(poi_id);

CREATE INDEX idx_district_files_district_id
    ON district_files(district_id);

CREATE INDEX idx_district_sources_district_id
    ON district_sources(district_id);
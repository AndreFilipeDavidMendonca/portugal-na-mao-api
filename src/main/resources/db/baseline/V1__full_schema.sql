-- =========================
-- APP USER
-- =========================
CREATE TABLE app_user (
                          id UUID PRIMARY KEY,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          first_name VARCHAR(120),
                          last_name VARCHAR(120),
                          age INTEGER,
                          nationality VARCHAR(120),
                          phone VARCHAR(50),
                          display_name VARCHAR(255),
                          avatar_url TEXT,
                          password_hash VARCHAR(255) NOT NULL,
                          role VARCHAR(32) NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =========================
-- DISTRICT
-- =========================
CREATE TABLE district (
                          id BIGSERIAL PRIMARY KEY,
                          code VARCHAR(10) UNIQUE,
                          name VARCHAR(100) NOT NULL,
                          name_pt VARCHAR(100),
                          population INTEGER,
                          founded_year INTEGER,
                          lat DOUBLE PRECISION,
                          lon DOUBLE PRECISION,
                          description TEXT,
                          inhabited_since VARCHAR(255),
                          history TEXT,
                          municipalities_count INTEGER,
                          parishes_count INTEGER
);

CREATE TABLE district_files (
                                district_id BIGINT NOT NULL,
                                file_url TEXT,
                                CONSTRAINT fk_district_files_district
                                    FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

CREATE TABLE district_sources (
                                  district_id BIGINT NOT NULL,
                                  source TEXT,
                                  CONSTRAINT fk_district_sources_district
                                      FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

-- =========================
-- POI
-- =========================
CREATE TABLE poi (
                     id BIGSERIAL PRIMARY KEY,
                     owner_id UUID NULL,
                     district_id BIGINT NULL,
                     name VARCHAR(255) NOT NULL,
                     name_pt VARCHAR(255),
                     category VARCHAR(255) NOT NULL,
                     subcategory VARCHAR(100),
                     description TEXT,
                     wikipedia_url TEXT,
                     sipa_id VARCHAR(100),
                     external_osm_id VARCHAR(100),
                     lat DOUBLE PRECISION,
                     lon DOUBLE PRECISION,
                     source VARCHAR(50),
                     architect TEXT,
                     year_text VARCHAR(100),
                     created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                     CONSTRAINT fk_poi_owner
                         FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE SET NULL,

                     CONSTRAINT fk_poi_district
                         FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE SET NULL
);

CREATE TABLE poi_image (
                           id BIGSERIAL PRIMARY KEY,
                           poi_id BIGINT NOT NULL,
                           position INTEGER NOT NULL,
                           data TEXT NOT NULL,

                           CONSTRAINT fk_poi_image_poi
                               FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE
);

CREATE TABLE poi_comment (
                             id BIGSERIAL PRIMARY KEY,
                             poi_id BIGINT NOT NULL,
                             user_id UUID NOT NULL,
                             author_name VARCHAR(120) NOT NULL,
                             body TEXT NOT NULL,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             updated_at TIMESTAMPTZ NULL,

                             CONSTRAINT fk_poi_comment_poi
                                 FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE,

                             CONSTRAINT fk_poi_comment_user
                                 FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- =========================
-- FAVORITE
-- =========================
CREATE TABLE favorite (
                          id BIGSERIAL PRIMARY KEY,
                          user_id UUID NOT NULL,
                          poi_id BIGINT NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT uk_favorite_user_poi UNIQUE (user_id, poi_id),

                          CONSTRAINT fk_favorite_user
                              FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,

                          CONSTRAINT fk_favorite_poi
                              FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE
);

-- =========================
-- FRIENDSHIP
-- =========================
CREATE TABLE friendship (
                            id UUID PRIMARY KEY,
                            requester_id UUID NOT NULL,
                            receiver_id UUID NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                            CONSTRAINT uk_friendship_requester_receiver
                                UNIQUE (requester_id, receiver_id),

                            CONSTRAINT fk_friendship_requester
                                FOREIGN KEY (requester_id) REFERENCES app_user(id) ON DELETE CASCADE,

                            CONSTRAINT fk_friendship_receiver
                                FOREIGN KEY (receiver_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- =========================
-- MEDIA ITEM
-- =========================
CREATE TABLE media_item (
                            id BIGSERIAL PRIMARY KEY,
                            poi_id BIGINT NULL,
                            district_id BIGINT NULL,
                            type VARCHAR(50) NOT NULL,
                            provider VARCHAR(50),
                            external_id VARCHAR(200),
                            title TEXT,
                            url TEXT NOT NULL,
                            thumb_url TEXT,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                            CONSTRAINT fk_media_item_poi
                                FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE,

                            CONSTRAINT fk_media_item_district
                                FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

-- =========================
-- CHAT
-- =========================
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
                              receiver_id UUID NOT NULL,
                              type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                              body VARCHAR(4000),
                              poi_id BIGINT NULL,
                              poi_name VARCHAR(300),
                              poi_image VARCHAR(2000),
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              read_at TIMESTAMPTZ NULL,

                              CONSTRAINT fk_chat_message_conversation
                                  FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE,

                              CONSTRAINT fk_chat_message_sender
                                  FOREIGN KEY (sender_id) REFERENCES app_user(id) ON DELETE CASCADE,

                              CONSTRAINT fk_chat_message_receiver
                                  FOREIGN KEY (receiver_id) REFERENCES app_user(id) ON DELETE CASCADE,

                              CONSTRAINT fk_chat_message_poi
                                  FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE SET NULL
);
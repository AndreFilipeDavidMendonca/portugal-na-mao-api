-- =============================================================================
-- Projeto .pt — Bootstrap DB (PostgreSQL, idempotente)
-- Objetivo: reconciliar a estrutura base com o estado esperado pelo backend.
-- Este script pode correr várias vezes.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION norm_text(input TEXT)
RETURNS TEXT AS $$
BEGIN
  IF input IS NULL THEN
    RETURN '';
  END IF;
  RETURN unaccent(lower(input));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE TABLE IF NOT EXISTS app_bootstrap_state (
  script_name VARCHAR(120) PRIMARY KEY,
  checksum VARCHAR(128) NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS district (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(10),
  name VARCHAR(100) NOT NULL,
  name_pt VARCHAR(100),
  description TEXT,
  population INTEGER,
  founded_year INTEGER,
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION,
  inhabited_since VARCHAR(255),
  history TEXT,
  municipalities_count INTEGER,
  parishes_count INTEGER
);

ALTER TABLE district ADD COLUMN IF NOT EXISTS code VARCHAR(10);
ALTER TABLE district ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE district ADD COLUMN IF NOT EXISTS name_pt VARCHAR(100);
ALTER TABLE district ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE district ADD COLUMN IF NOT EXISTS population INTEGER;
ALTER TABLE district ADD COLUMN IF NOT EXISTS founded_year INTEGER;
ALTER TABLE district ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION;
ALTER TABLE district ADD COLUMN IF NOT EXISTS lon DOUBLE PRECISION;
ALTER TABLE district ADD COLUMN IF NOT EXISTS inhabited_since VARCHAR(255);
ALTER TABLE district ADD COLUMN IF NOT EXISTS history TEXT;
ALTER TABLE district ADD COLUMN IF NOT EXISTS municipalities_count INTEGER;
ALTER TABLE district ADD COLUMN IF NOT EXISTS parishes_count INTEGER;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'district' AND column_name = 'name') THEN
    EXECUTE 'ALTER TABLE district ALTER COLUMN name SET NOT NULL';
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS district_files (
  district_id BIGINT NOT NULL,
  file_url TEXT,
  PRIMARY KEY (district_id, file_url),
  CONSTRAINT fk_district_files_district FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS district_sources (
  district_id BIGINT NOT NULL,
  source TEXT,
  PRIMARY KEY (district_id, source),
  CONSTRAINT fk_district_sources_district FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS app_user (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) NOT NULL UNIQUE,
  first_name VARCHAR(120),
  last_name VARCHAR(120),
  age INTEGER,
  nationality VARCHAR(120),
  phone VARCHAR(50),
  display_name VARCHAR(255),
  avatar_url TEXT,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS first_name VARCHAR(120);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_name VARCHAR(120);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS age INTEGER;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS nationality VARCHAR(120);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS poi (
  id BIGSERIAL PRIMARY KEY,
  owner_id UUID,
  district_id BIGINT,
  name VARCHAR(255) NOT NULL,
  name_pt VARCHAR(255),
  category VARCHAR(100) NOT NULL,
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
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE poi ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS district_id BIGINT;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS name_pt VARCHAR(255);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS subcategory VARCHAR(100);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS wikipedia_url TEXT;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS sipa_id VARCHAR(100);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS external_osm_id VARCHAR(100);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS lon DOUBLE PRECISION;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS source VARCHAR(50);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS architect TEXT;
ALTER TABLE poi ADD COLUMN IF NOT EXISTS year_text VARCHAR(100);
ALTER TABLE poi ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE poi ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS poi_image (
  id BIGSERIAL PRIMARY KEY,
  poi_id BIGINT NOT NULL,
  position INTEGER NOT NULL,
  data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS media_item (
  id BIGSERIAL PRIMARY KEY,
  poi_id BIGINT,
  district_id BIGINT,
  type VARCHAR(50) NOT NULL,
  provider VARCHAR(50),
  external_id VARCHAR(200),
  title TEXT,
  url TEXT NOT NULL,
  thumb_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS favorite (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL,
  poi_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_favorite_user_poi UNIQUE (user_id, poi_id)
);

CREATE TABLE IF NOT EXISTS poi_comment (
  id BIGSERIAL PRIMARY KEY,
  poi_id BIGINT NOT NULL,
  user_id UUID NOT NULL,
  author_name VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS friendship (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  requester_id UUID NOT NULL,
  receiver_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_friendship_requester_receiver UNIQUE (requester_id, receiver_id)
);

CREATE TABLE IF NOT EXISTS chat_conversation (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_a_id UUID NOT NULL,
  user_b_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_message_type VARCHAR(20),
  last_message_body TEXT,
  last_poi_id BIGINT,
  last_poi_name VARCHAR(300),
  last_poi_image VARCHAR(2000),
  last_message_at TIMESTAMPTZ,
  CONSTRAINT uk_chat_conversation_user_a_user_b UNIQUE (user_a_id, user_b_id),
  CONSTRAINT chk_chat_conversation_distinct_users CHECK (user_a_id <> user_b_id)
);

ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_message_type VARCHAR(20);
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_message_body TEXT;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_poi_id BIGINT;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_poi_name VARCHAR(300);
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_poi_image VARCHAR(2000);
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS chat_message (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  conversation_id UUID NOT NULL,
  sender_id UUID NOT NULL,
  receiver_id UUID,
  type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  body VARCHAR(4000),
  poi_id BIGINT,
  poi_name VARCHAR(300),
  poi_image VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  read_at TIMESTAMPTZ NULL
);

ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS receiver_id UUID;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS body VARCHAR(4000);
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS poi_id BIGINT;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS poi_name VARCHAR(300);
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS poi_image VARCHAR(2000);
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ NULL;

DO $$
BEGIN
  BEGIN
    ALTER TABLE chat_message ALTER COLUMN body DROP NOT NULL;
  EXCEPTION
    WHEN others THEN NULL;
  END;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_poi_owner') THEN
    ALTER TABLE poi ADD CONSTRAINT fk_poi_owner FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE SET NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_poi_district') THEN
    ALTER TABLE poi ADD CONSTRAINT fk_poi_district FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE SET NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_poi_image_poi') THEN
    ALTER TABLE poi_image ADD CONSTRAINT fk_poi_image_poi FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_media_item_poi') THEN
    ALTER TABLE media_item ADD CONSTRAINT fk_media_item_poi FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_media_item_district') THEN
    ALTER TABLE media_item ADD CONSTRAINT fk_media_item_district FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_favorite_user') THEN
    ALTER TABLE favorite ADD CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_favorite_poi') THEN
    ALTER TABLE favorite ADD CONSTRAINT fk_favorite_poi FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_poi_comment_poi') THEN
    ALTER TABLE poi_comment ADD CONSTRAINT fk_poi_comment_poi FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_poi_comment_user') THEN
    ALTER TABLE poi_comment ADD CONSTRAINT fk_poi_comment_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_friendship_requester') THEN
    ALTER TABLE friendship ADD CONSTRAINT fk_friendship_requester FOREIGN KEY (requester_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_friendship_receiver') THEN
    ALTER TABLE friendship ADD CONSTRAINT fk_friendship_receiver FOREIGN KEY (receiver_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_conversation_user_a') THEN
    ALTER TABLE chat_conversation ADD CONSTRAINT fk_chat_conversation_user_a FOREIGN KEY (user_a_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_conversation_user_b') THEN
    ALTER TABLE chat_conversation ADD CONSTRAINT fk_chat_conversation_user_b FOREIGN KEY (user_b_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_message_conversation') THEN
    ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_message_sender') THEN
    ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_message_receiver') THEN
    ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_receiver FOREIGN KEY (receiver_id) REFERENCES app_user(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_message_poi') THEN
    ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_poi FOREIGN KEY (poi_id) REFERENCES poi(id) ON DELETE SET NULL;
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_district_code ON district(lower(code)) WHERE code IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_district_name_pt ON district(norm_text(name_pt)) WHERE name_pt IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email ON app_user(lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_sipa_id ON poi(sipa_id) WHERE sipa_id IS NOT NULL AND sipa_id <> '';
CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_external_osm_id ON poi(external_osm_id) WHERE external_osm_id IS NOT NULL AND external_osm_id <> '';
CREATE INDEX IF NOT EXISTS idx_poi_owner_id ON poi(owner_id);
CREATE INDEX IF NOT EXISTS idx_poi_district ON poi(district_id);
CREATE INDEX IF NOT EXISTS idx_poi_category ON poi(category);
CREATE INDEX IF NOT EXISTS idx_poi_coordinates ON poi(lat, lon);
CREATE INDEX IF NOT EXISTS idx_poi_name_norm ON poi(norm_text(coalesce(name_pt, name)));
CREATE INDEX IF NOT EXISTS idx_district_name_norm ON district(norm_text(coalesce(name_pt, name)));
CREATE INDEX IF NOT EXISTS idx_media_poi ON media_item(poi_id);
CREATE INDEX IF NOT EXISTS idx_media_district ON media_item(district_id);
CREATE INDEX IF NOT EXISTS idx_favorite_user ON favorite(user_id);
CREATE INDEX IF NOT EXISTS idx_poi_comment_poi ON poi_comment(poi_id);
CREATE INDEX IF NOT EXISTS idx_friendship_requester ON friendship(requester_id);
CREATE INDEX IF NOT EXISTS idx_friendship_receiver ON friendship(receiver_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_a_id ON chat_conversation(user_a_id);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_b_id ON chat_conversation(user_b_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_id_created_at ON chat_message(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_message_sender_id ON chat_message(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_receiver_id ON chat_message(receiver_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_receiver_id_read_at ON chat_message(receiver_id, read_at);
CREATE INDEX IF NOT EXISTS idx_chat_message_type ON chat_message(type);
CREATE INDEX IF NOT EXISTS idx_chat_message_poi_id ON chat_message(poi_id);

DROP TRIGGER IF EXISTS trg_app_user_updated_at ON app_user;
CREATE TRIGGER trg_app_user_updated_at BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_chat_conversation_updated_at ON chat_conversation;
CREATE TRIGGER trg_chat_conversation_updated_at BEFORE UPDATE ON chat_conversation FOR EACH ROW EXECUTE FUNCTION set_updated_at();

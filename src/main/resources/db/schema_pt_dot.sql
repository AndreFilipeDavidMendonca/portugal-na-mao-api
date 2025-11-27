-- ==============================
-- SCHEMA pt_dot
-- Projeto .pt — infra de dados
-- ==============================

-- Garantir extensão UUID (opcional mas útil)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==============================
-- Distritos
-- ==============================
CREATE TABLE district (
                          id SERIAL PRIMARY KEY,
                          code VARCHAR(20),
                          name VARCHAR(200) NOT NULL,
                          name_pt VARCHAR(200),
                          description TEXT,
                          population INTEGER,
                          founded_year INTEGER,
                          lat DOUBLE PRECISION,
                          lon DOUBLE PRECISION,
                          created_at TIMESTAMP DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW()
);

-- ==============================
-- POIs (monumentos, parques, ruínas, etc.)
-- ==============================
CREATE TABLE poi (
                     id SERIAL PRIMARY KEY,
                     district_id INTEGER NOT NULL REFERENCES district(id) ON DELETE CASCADE,
                     name VARCHAR(255) NOT NULL,
                     name_pt VARCHAR(255),
                     category VARCHAR(100) NOT NULL,        -- castle / church / park / viewpoint / ruins / palace / monument...
                     subcategory VARCHAR(100),
                     description TEXT,
                     wikipedia_url TEXT,
                     sipa_id VARCHAR(100),
                     external_osm_id VARCHAR(100),
                     lat DOUBLE PRECISION NOT NULL,
                     lon DOUBLE PRECISION NOT NULL,
                     source VARCHAR(50) DEFAULT 'script',   -- overpass / google / script / manual
                     created_at TIMESTAMP DEFAULT NOW(),
                     updated_at TIMESTAMP DEFAULT NOW()
);

-- ==============================
-- Media: vídeos, fotos, thumbs
-- ==============================
CREATE TABLE media_item (
                            id SERIAL PRIMARY KEY,
                            poi_id INTEGER REFERENCES poi(id) ON DELETE CASCADE,
                            district_id INTEGER REFERENCES district(id) ON DELETE CASCADE,
                            type VARCHAR(50) NOT NULL,          -- video / image / thumbnail
                            provider VARCHAR(50),               -- youtube / google / afonso_arriba / manual
                            external_id VARCHAR(200),
                            title TEXT,
                            url TEXT NOT NULL,
                            thumb_url TEXT,
                            created_at TIMESTAMP DEFAULT NOW()
);

-- ==============================
-- Users (SSO futuro)
-- ==============================
CREATE TABLE app_user (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          email VARCHAR(255) UNIQUE NOT NULL,
                          display_name VARCHAR(255),
                          avatar_url TEXT,
                          created_at TIMESTAMP DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW()
);

-- ==============================
-- Favoritos
-- ==============================
CREATE TABLE favorite (
                          id SERIAL PRIMARY KEY,
                          user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
                          poi_id INTEGER REFERENCES poi(id) ON DELETE CASCADE,
                          created_at TIMESTAMP DEFAULT NOW(),
                          UNIQUE (user_id, poi_id)
);

-- ==============================
-- Comentários
-- ==============================
CREATE TABLE comment (
                         id SERIAL PRIMARY KEY,
                         user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
                         poi_id INTEGER REFERENCES poi(id) ON DELETE CASCADE,
                         content TEXT NOT NULL,
                         created_at TIMESTAMP DEFAULT NOW()
);

-- ==============================
-- Índices essenciais
-- ==============================
CREATE INDEX idx_poi_district ON poi(district_id);
CREATE INDEX idx_poi_category ON poi(category);
CREATE INDEX idx_poi_coordinates ON poi(lat, lon);
CREATE INDEX idx_media_poi ON media_item(poi_id);
CREATE INDEX idx_favorite_user ON favorite(user_id);
CREATE INDEX idx_comment_poi ON comment(poi_id);
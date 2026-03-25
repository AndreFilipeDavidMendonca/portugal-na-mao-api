-- =============================================================================
-- Projeto .pt — Bootstrap DB (PostgreSQL)
-- Objetivo: criar base de dados pt_dot (schema público) com estrutura estável
-- Inclui: extensões, tabelas, constraints, índices, views e comandos utilitários
-- =============================================================================

-- Recomendado: correr isto dentro da BD "pt_dot" já criada.
-- Ex (docker): docker compose exec postgres psql -U ptdot_user -d pt_dot -f /sql/db_bootstrap.sql

-- -----------------------------------------------------------------------------
-- 0) Extensões úteis
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS unaccent;

-- -----------------------------------------------------------------------------
-- 1) Funções utilitárias
-- -----------------------------------------------------------------------------

-- Atualizar automaticamente updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Normalização para pesquisa (accent-insensitive + lowercase)
CREATE OR REPLACE FUNCTION norm_text(input TEXT)
RETURNS TEXT AS $$
BEGIN
  IF input IS NULL THEN
    RETURN '';
END IF;
RETURN unaccent(lower(input));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- -----------------------------------------------------------------------------
-- 2) Tabelas
-- -----------------------------------------------------------------------------

-- 2.1 Distritos
CREATE TABLE IF NOT EXISTS district (
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

-- Evitar duplicados “óbvios”
CREATE UNIQUE INDEX IF NOT EXISTS ux_district_code
    ON district (lower(code))
    WHERE code IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_district_name_pt
    ON district (norm_text(name_pt))
    WHERE name_pt IS NOT NULL;

-- Trigger updated_at
DROP TRIGGER IF EXISTS trg_district_updated_at ON district;
CREATE TRIGGER trg_district_updated_at
    BEFORE UPDATE ON district
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 2.2 POIs
CREATE TABLE IF NOT EXISTS poi (
                                   id SERIAL PRIMARY KEY,
                                   district_id INTEGER NOT NULL REFERENCES district(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,
    name_pt VARCHAR(255),

    category VARCHAR(100) NOT NULL,     -- ex: castle/church/park/viewpoint/ruins/palace/monument...
    subcategory VARCHAR(100),

    description TEXT,
    wikipedia_url TEXT,
    sipa_id VARCHAR(100),
    external_osm_id VARCHAR(100),

    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,

    source VARCHAR(50) DEFAULT 'script', -- overpass / script / manual / etc.
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
    );

-- “Chaves” externas opcionais
CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_sipa_id
    ON poi (sipa_id)
    WHERE sipa_id IS NOT NULL AND sipa_id <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_external_osm_id
    ON poi (external_osm_id)
    WHERE external_osm_id IS NOT NULL AND external_osm_id <> '';

-- Trigger updated_at
DROP TRIGGER IF EXISTS trg_poi_updated_at ON poi;
CREATE TRIGGER trg_poi_updated_at
    BEFORE UPDATE ON poi
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 2.3 Media
CREATE TABLE IF NOT EXISTS media_item (
                                          id SERIAL PRIMARY KEY,
                                          poi_id INTEGER REFERENCES poi(id) ON DELETE CASCADE,
    district_id INTEGER REFERENCES district(id) ON DELETE CASCADE,

    type VARCHAR(50) NOT NULL,          -- video / image / thumbnail
    provider VARCHAR(50),               -- youtube / wikimedia / afonso_arriba / manual
    external_id VARCHAR(200),
    title TEXT,
    url TEXT NOT NULL,
    thumb_url TEXT,

    created_at TIMESTAMP DEFAULT NOW()
    );

-- 2.4 Users
CREATE TABLE IF NOT EXISTS app_user (
                                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
    );

DROP TRIGGER IF EXISTS trg_app_user_updated_at ON app_user;
CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 2.5 Favoritos
CREATE TABLE IF NOT EXISTS favorite (
                                        id SERIAL PRIMARY KEY,
                                        user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, poi_id)
    );

-- 2.6 Comentários
CREATE TABLE IF NOT EXISTS comment (
                                       id SERIAL PRIMARY KEY,
                                       user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
    );

-- -----------------------------------------------------------------------------
-- 3) Índices essenciais
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_poi_district ON poi(district_id);
CREATE INDEX IF NOT EXISTS idx_poi_category ON poi(category);
CREATE INDEX IF NOT EXISTS idx_poi_coordinates ON poi(lat, lon);

-- Pesquisa rápida por nome (accent-insensitive)
CREATE INDEX IF NOT EXISTS idx_poi_name_norm
    ON poi (norm_text(coalesce(name_pt, name)));

CREATE INDEX IF NOT EXISTS idx_district_name_norm
    ON district (norm_text(coalesce(name_pt, name)));

CREATE INDEX IF NOT EXISTS idx_media_poi ON media_item(poi_id);
CREATE INDEX IF NOT EXISTS idx_favorite_user ON favorite(user_id);
CREATE INDEX IF NOT EXISTS idx_comment_poi ON comment(poi_id);

-- -----------------------------------------------------------------------------
-- 4) Views úteis para debug/relatórios
-- -----------------------------------------------------------------------------

-- 4.1 Contagem de POIs por distrito e categoria
CREATE OR REPLACE VIEW v_poi_counts_by_district AS
SELECT
    d.id AS district_id,
    coalesce(d.name_pt, d.name) AS district_name,
    p.category,
    count(*) AS total
FROM district d
         JOIN poi p ON p.district_id = d.id
GROUP BY d.id, coalesce(d.name_pt, d.name), p.category
ORDER BY district_name, total DESC;

-- 4.2 POIs “completos” (para admin/debug)
CREATE OR REPLACE VIEW v_poi_full AS
SELECT
    p.*,
    coalesce(d.name_pt, d.name) AS district_name
FROM poi p
         JOIN district d ON d.id = p.district_id;

-- -----------------------------------------------------------------------------
-- 5) Queries úteis (copiar/colar no dia-a-dia)
-- -----------------------------------------------------------------------------
-- Ver distritos
-- SELECT id, code, coalesce(name_pt, name) AS name FROM district ORDER BY 3;

-- Procurar distrito por nome (accent-insensitive)
-- SELECT * FROM district
-- WHERE norm_text(coalesce(name_pt, name)) LIKE '%' || norm_text('Lisboa') || '%'
-- ORDER BY coalesce(name_pt, name);

-- Procurar POIs por texto
-- SELECT id, district_id, category, coalesce(name_pt, name) AS name
-- FROM poi
-- WHERE norm_text(coalesce(name_pt, name)) LIKE '%' || norm_text('castelo') || '%'
-- ORDER BY name
-- LIMIT 50;

-- Top categorias
-- SELECT category, count(*) FROM poi GROUP BY category ORDER BY 2 DESC;

-- Últimos comentários
-- SELECT c.id, c.created_at, u.email, p.name_pt, c.content
-- FROM comment c
-- JOIN app_user u ON u.id = c.user_id
-- JOIN poi p ON p.id = c.poi_id
-- ORDER BY c.created_at DESC
-- LIMIT 20;

-- -----------------------------------------------------------------------------
-- 6) Seeds opcionais (descomenta se quiseres)
-- -----------------------------------------------------------------------------
-- INSERT INTO district (code, name, name_pt, lat, lon)
-- VALUES ('LIS', 'Lisbon', 'Lisboa', 38.7167, -9.1333)
-- ON CONFLICT DO NOTHING;

-- =============================================================================
-- FIM
-- =============================================================================
-- =========================================================
-- BASELINE COMPLETA DO SCHEMA
-- Estado atual consolidado da BD
-- =========================================================

-- ---------------------------------------------------------
-- app_user
-- ---------------------------------------------------------
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
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT chk_app_user_role
                              CHECK (role IN ('ADMIN', 'USER', 'BUSINESS'))
);

-- ---------------------------------------------------------
-- district
-- ---------------------------------------------------------
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
                                file_url TEXT NOT NULL,

                                CONSTRAINT fk_district_files_district
                                    FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

CREATE TABLE district_sources (
                                  district_id BIGINT NOT NULL,
                                  source TEXT NOT NULL,

                                  CONSTRAINT fk_district_sources_district
                                      FOREIGN KEY (district_id) REFERENCES district(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------
-- poi
-- ---------------------------------------------------------
CREATE TABLE poi (
                     id BIGSERIAL PRIMARY KEY,
                     owner_id UUID NULL,
                     district_id
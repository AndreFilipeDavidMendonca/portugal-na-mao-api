CREATE INDEX IF NOT EXISTS idx_poi_district_id ON poi(district_id);
CREATE INDEX IF NOT EXISTS idx_poi_category ON poi(category);
CREATE INDEX IF NOT EXISTS idx_favorite_user_id ON favorite(user_id);
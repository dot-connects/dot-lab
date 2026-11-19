CREATE TABLE IF NOT EXISTS cars (group_id TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, ord INTEGER NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY (group_id, id));
CREATE TABLE IF NOT EXISTS parks2 (group_id TEXT NOT NULL, car_id TEXT NOT NULL, json TEXT NOT NULL, prev_json TEXT, by_name TEXT, by_id TEXT, driving_by TEXT, updated INTEGER NOT NULL, PRIMARY KEY (group_id, car_id));
ALTER TABLE members ADD COLUMN last_seen INTEGER;
ALTER TABLE groups ADD COLUMN name TEXT;
-- Backfill (safe to re-run): rows that predate last_seen count as seen now, so the 30-day inactivity rule starts from the migration.
UPDATE members SET last_seen = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE last_seen IS NULL;

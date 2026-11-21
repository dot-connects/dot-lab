-- v3 tables coexist with v1/v2 until the maintenance cutover.
CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, secret_hash TEXT NOT NULL, name TEXT NOT NULL,
  fcm_token TEXT, created INTEGER NOT NULL, last_seen INTEGER, v2_seen INTEGER);
CREATE INDEX IF NOT EXISTS devices_token ON devices (fcm_token);
CREATE TABLE IF NOT EXISTS memberships (group_id TEXT NOT NULL, device_id TEXT NOT NULL,
  owner INTEGER NOT NULL DEFAULT 0, joined INTEGER NOT NULL, PRIMARY KEY (group_id, device_id));
CREATE INDEX IF NOT EXISTS memberships_device ON memberships (device_id);
CREATE TABLE IF NOT EXISTS cars3 (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, json TEXT NOT NULL,
  ord INTEGER NOT NULL, updated INTEGER NOT NULL, legacy_group TEXT, legacy_car_id TEXT);
CREATE INDEX IF NOT EXISTS cars3_owner ON cars3 (owner_id);
CREATE TABLE IF NOT EXISTS car_groups (car_id TEXT NOT NULL, group_id TEXT NOT NULL, added INTEGER NOT NULL,
  PRIMARY KEY (car_id, group_id));
CREATE INDEX IF NOT EXISTS car_groups_group ON car_groups (group_id);
CREATE TABLE IF NOT EXISTS parks3 (car_id TEXT PRIMARY KEY, json TEXT NOT NULL, prev_json TEXT,
  by_name TEXT, by_id TEXT, driving_by TEXT, driving_by_id TEXT, updated INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS car_map (old_group TEXT NOT NULL, old_id TEXT NOT NULL, new_id TEXT NOT NULL,
  PRIMARY KEY (old_group, old_id));
CREATE INDEX IF NOT EXISTS requests_group ON requests (group_id, by_id);
CREATE INDEX IF NOT EXISTS requests_code ON requests (code);
CREATE INDEX IF NOT EXISTS invites_group ON invites (group_id);

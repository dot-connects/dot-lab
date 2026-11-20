-- D1 schema for dot-lab-parking. Apply: npx wrangler@4 d1 execute dot-lab-parking --remote --file schema.sql
CREATE TABLE IF NOT EXISTS groups (id TEXT PRIMARY KEY, created INTEGER NOT NULL, name TEXT);
CREATE TABLE IF NOT EXISTS members (
  id TEXT PRIMARY KEY,
  group_id TEXT NOT NULL,
  name TEXT NOT NULL,
  secret_hash TEXT NOT NULL,
  fcm_token TEXT,
  owner INTEGER NOT NULL DEFAULT 0,
  created INTEGER NOT NULL,
  last_seen INTEGER
);
CREATE INDEX IF NOT EXISTS members_group ON members (group_id);
CREATE TABLE IF NOT EXISTS invites (code_hash TEXT PRIMARY KEY, group_id TEXT NOT NULL, expires INTEGER NOT NULL);
-- join requests: req_hash = SHA-256 hex of the requester's secret (the /r link carries only this hash), code = the invite code (normalized) handed to it
CREATE TABLE IF NOT EXISTS requests (req_hash TEXT PRIMARY KEY, group_id TEXT NOT NULL, code TEXT NOT NULL, expires INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS parks (
  group_id TEXT PRIMARY KEY,
  json TEXT NOT NULL,
  by_name TEXT,
  by_id TEXT,
  updated INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS cars (group_id TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, ord INTEGER NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY (group_id, id));
CREATE TABLE IF NOT EXISTS parks2 (group_id TEXT NOT NULL, car_id TEXT NOT NULL, json TEXT NOT NULL, prev_json TEXT, by_name TEXT, by_id TEXT, driving_by TEXT, updated INTEGER NOT NULL, PRIMARY KEY (group_id, car_id));

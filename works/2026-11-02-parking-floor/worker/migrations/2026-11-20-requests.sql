-- Join requests (request-to-join link /r). Add-only, safe to re-run.
-- Apply: npx wrangler@4 d1 execute dot-lab-parking --remote --file migrations/2026-11-20-requests.sql
CREATE TABLE IF NOT EXISTS requests (req_hash TEXT PRIMARY KEY, group_id TEXT NOT NULL, code TEXT NOT NULL, expires INTEGER NOT NULL);

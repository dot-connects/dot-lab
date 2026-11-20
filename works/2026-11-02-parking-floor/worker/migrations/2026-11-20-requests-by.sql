-- Join requests remember who accepted them (the requester sees the name; the rows go with that member). Add-only.
-- Not re-runnable (ALTER fails once the column exists). Apply once: npx wrangler@4 d1 execute dot-lab-parking --remote --file migrations/2026-11-20-requests-by.sql
ALTER TABLE requests ADD COLUMN by_id TEXT;
ALTER TABLE requests ADD COLUMN by_name TEXT;

-- day counters for the transform cost guard: k = "<KST day>|all" or "<KST day>|<device id>"
CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL);

-- Play purchases (one token = one sheet): state drawing | done | failed; sheet_key = the R2 object of a done sheet
CREATE TABLE IF NOT EXISTS purchases (token TEXT PRIMARY KEY, product TEXT NOT NULL, device TEXT NOT NULL,
  state TEXT NOT NULL, sheet_key TEXT, layout TEXT, consumed INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL);
-- test-period free sheets used per device
CREATE TABLE IF NOT EXISTS free (device TEXT PRIMARY KEY, n INTEGER NOT NULL);
-- in-app reports of AI pictures (Play AI-generated content policy)
CREATE TABLE IF NOT EXISTS reports (at INTEGER NOT NULL, device TEXT, entry TEXT, kind TEXT, style TEXT, reason TEXT);

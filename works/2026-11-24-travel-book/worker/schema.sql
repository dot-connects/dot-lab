-- k = "m|<KST month>" (mills spent) or "d|<KST day>" (books made). Nothing about the request itself is stored.
CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL);
-- /layout background jobs (migrations/0002_jobs.sql). body = plan JSON (done) or {"error":...} (failed); never images or
-- prompts. month = the counts key the reservation went to. Pruned after 24 h by the cron.
CREATE TABLE IF NOT EXISTS jobs (job TEXT PRIMARY KEY, resp TEXT, month TEXT, reserved INTEGER, status TEXT, body TEXT, created INTEGER);
CREATE TABLE IF NOT EXISTS made (dev TEXT NOT NULL, book TEXT NOT NULL, created INTEGER, PRIMARY KEY (dev, book));

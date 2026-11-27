-- /layout background jobs. body = plan JSON (done) or {"error":...} (failed); never images or prompts. Pruned after 24 h.
CREATE TABLE IF NOT EXISTS jobs (job TEXT PRIMARY KEY, resp TEXT, month TEXT, reserved INTEGER, status TEXT, body TEXT, created INTEGER);

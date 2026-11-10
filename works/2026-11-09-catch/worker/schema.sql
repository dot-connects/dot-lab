-- day counters for the transform cost guard: k = "<KST day>|all" or "<KST day>|<device id>"
CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL);

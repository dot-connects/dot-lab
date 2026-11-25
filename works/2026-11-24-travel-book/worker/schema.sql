-- k = "m|<KST month>" (mills spent) or "d|<KST day>" (books made). Nothing about the request itself is stored.
CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL);

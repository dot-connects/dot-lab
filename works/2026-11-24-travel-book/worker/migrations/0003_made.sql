-- books each phone has made (free books are counted here, not on the phone). dev = SHA-256 of the phone's ANDROID_ID,
-- book = the app's book id. Nothing else.
CREATE TABLE IF NOT EXISTS made (dev TEXT NOT NULL, book TEXT NOT NULL, created INTEGER, PRIMARY KEY (dev, book));

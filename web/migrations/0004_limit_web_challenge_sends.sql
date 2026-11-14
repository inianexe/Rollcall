CREATE TABLE IF NOT EXISTS web_challenge_sends (
  uid TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS web_challenge_sends_uid_created
  ON web_challenge_sends(uid, created_at);

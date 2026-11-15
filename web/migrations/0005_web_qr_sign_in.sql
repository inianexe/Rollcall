CREATE TABLE IF NOT EXISTS web_qr_challenges (
  id TEXT PRIMARY KEY,
  secret_hash TEXT NOT NULL,
  state TEXT NOT NULL CHECK(state IN ('pending', 'approved', 'denied', 'consumed')),
  uid TEXT,
  browser_label TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);
CREATE INDEX IF NOT EXISTS web_qr_challenges_expiry ON web_qr_challenges(expires_at, state);
CREATE INDEX IF NOT EXISTS web_qr_challenges_uid ON web_qr_challenges(uid);

CREATE TABLE IF NOT EXISTS web_qr_challenge_sends (
  ip_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS web_qr_challenge_sends_ip_created
  ON web_qr_challenge_sends(ip_hash, created_at);

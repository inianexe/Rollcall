CREATE TABLE IF NOT EXISTS pending_signups (
  email TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  used_at INTEGER
);

CREATE TABLE IF NOT EXISTS signup_verification_sends (
  email TEXT NOT NULL,
  ip_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS signup_verification_sends_email_created
  ON signup_verification_sends(email, created_at DESC);

CREATE INDEX IF NOT EXISTS signup_verification_sends_created
  ON signup_verification_sends(ip_hash, created_at);

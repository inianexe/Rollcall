CREATE TABLE IF NOT EXISTS device_replacement_codes (
  uid TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  used_at INTEGER
);

CREATE TABLE IF NOT EXISTS device_replacement_email_sends (
  uid TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS device_replacement_email_sends_uid_created
  ON device_replacement_email_sends(uid, created_at);

DROP TABLE IF EXISTS recovery_codes;

CREATE TABLE IF NOT EXISTS trusted_devices (
  uid TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,
  fcm_token TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS snapshots (
  uid TEXT PRIMARY KEY,
  schema_version INTEGER NOT NULL,
  snapshot_json TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS web_challenges (
  id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  code_iv TEXT NOT NULL,
  code_cipher TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  consumed_at INTEGER
);
CREATE INDEX IF NOT EXISTS web_challenges_uid_created ON web_challenges(uid, created_at);

CREATE TABLE IF NOT EXISTS web_sessions (
  session_hash TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS web_sessions_uid ON web_sessions(uid);

CREATE TABLE IF NOT EXISTS recovery_codes (
  uid TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  used_at INTEGER,
  PRIMARY KEY(uid, code_hash)
);

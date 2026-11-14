CREATE TABLE IF NOT EXISTS approval_devices (
  device_id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  fcm_token TEXT NOT NULL,
  public_key TEXT,
  key_enrolled_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS approval_devices_uid ON approval_devices(uid);

INSERT OR IGNORE INTO approval_devices(device_id, uid, fcm_token, public_key, key_enrolled_at, created_at, updated_at)
  SELECT device_id, uid, fcm_token, NULL, NULL, updated_at, updated_at FROM trusted_devices;

ALTER TABLE web_challenges ADD COLUMN match_code TEXT;
ALTER TABLE web_challenges ADD COLUMN approved_at INTEGER;
ALTER TABLE web_challenges ADD COLUMN approved_by_device_id TEXT;
ALTER TABLE web_challenges ADD COLUMN denied_at INTEGER;

CREATE TABLE IF NOT EXISTS approval_enrollment_requests (
  id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  device_id TEXT NOT NULL,
  nonce_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  completed_at INTEGER
);
CREATE INDEX IF NOT EXISTS approval_enrollment_requests_uid ON approval_enrollment_requests(uid, created_at);

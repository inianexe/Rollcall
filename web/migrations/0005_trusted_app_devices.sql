CREATE TABLE IF NOT EXISTS app_devices (
  device_id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  fcm_token TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS app_devices_uid ON app_devices(uid);

INSERT OR IGNORE INTO app_devices(device_id, uid, fcm_token, updated_at)
  SELECT device_id, uid, fcm_token, updated_at FROM trusted_devices;

CREATE TABLE IF NOT EXISTS device_replacement_sends (
  uid TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS device_replacement_sends_uid_created
  ON device_replacement_sends(uid, created_at);
INSERT INTO device_replacement_sends(uid, created_at)
  SELECT uid, created_at FROM device_replacement_email_sends;
DROP TABLE device_replacement_email_sends;

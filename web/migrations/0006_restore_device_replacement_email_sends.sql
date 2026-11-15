CREATE TABLE IF NOT EXISTS device_replacement_email_sends (
  uid TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS device_replacement_email_sends_uid_created
  ON device_replacement_email_sends(uid, created_at);

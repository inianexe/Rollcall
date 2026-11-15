CREATE TABLE IF NOT EXISTS command_center_audit (
  id TEXT PRIMARY KEY,
  actor_uid TEXT NOT NULL,
  action TEXT NOT NULL,
  target_type TEXT,
  target_id TEXT,
  created_at INTEGER NOT NULL,
  result TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS command_center_audit_created
  ON command_center_audit(created_at DESC);

CREATE TABLE IF NOT EXISTS command_center_campaigns (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('announcement','feature','maintenance','outage','resolved','update','operational')),
  priority TEXT NOT NULL CHECK (priority IN ('normal','important','critical')),
  channels_json TEXT NOT NULL,
  action_label TEXT NOT NULL DEFAULT '',
  action_path TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL CHECK (status IN ('draft','published','revoked')),
  created_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  published_at INTEGER,
  expires_at INTEGER NOT NULL,
  push_status TEXT NOT NULL DEFAULT 'not_requested' CHECK (push_status IN ('not_requested','pending','accepted','failed'))
);

CREATE INDEX IF NOT EXISTS command_center_campaigns_active
  ON command_center_campaigns(status, expires_at, published_at DESC);

CREATE TABLE IF NOT EXISTS command_center_campaign_receipts (
  campaign_id TEXT NOT NULL REFERENCES command_center_campaigns(id) ON DELETE CASCADE,
  uid TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('read','dismissed')),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (campaign_id, uid)
);

CREATE INDEX IF NOT EXISTS command_center_campaign_receipts_uid
  ON command_center_campaign_receipts(uid, updated_at DESC);

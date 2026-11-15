CREATE TABLE IF NOT EXISTS devos_releases (
  id TEXT PRIMARY KEY,
  created_by TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN (
    'draft','uploaded','validation_queued','validating','validated',
    'publish_queued','publishing','published','failed','cancelled'
  )),
  channel TEXT NOT NULL CHECK (channel IN ('stable')),
  policy TEXT NOT NULL CHECK (policy IN ('optional','recommended','required')),
  notes TEXT NOT NULL,
  file_name TEXT NOT NULL DEFAULT '',
  size_bytes INTEGER NOT NULL DEFAULT 0,
  sha256 TEXT NOT NULL DEFAULT '',
  version_code INTEGER,
  version_name TEXT,
  min_sdk INTEGER,
  target_sdk INTEGER,
  signer_sha256 TEXT,
  failure_reason TEXT NOT NULL DEFAULT '',
  release_url TEXT NOT NULL DEFAULT '',
  push_status TEXT NOT NULL DEFAULT 'not_requested' CHECK (push_status IN ('not_requested','accepted','failed')),
  approval_code_hash TEXT,
  approval_expires_at INTEGER,
  approval_sent_at INTEGER,
  approval_attempts INTEGER NOT NULL DEFAULT 0,
  worker_attempts INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  validated_at INTEGER,
  published_at INTEGER
);

CREATE INDEX IF NOT EXISTS devos_releases_status_created
  ON devos_releases(status, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS devos_releases_published_version
  ON devos_releases(version_code) WHERE status='published';

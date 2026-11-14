ALTER TABLE web_sessions ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0;
UPDATE web_sessions SET expires_at=created_at+2592000 WHERE expires_at=0;
CREATE INDEX IF NOT EXISTS web_sessions_expires_at ON web_sessions(expires_at);

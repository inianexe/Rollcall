ALTER TABLE web_sessions ADD COLUMN session_id TEXT;
ALTER TABLE web_sessions ADD COLUMN user_agent TEXT NOT NULL DEFAULT '';
ALTER TABLE web_sessions ADD COLUMN last_used_at INTEGER NOT NULL DEFAULT 0;

UPDATE web_sessions SET session_id=lower(hex(randomblob(16))), last_used_at=created_at WHERE session_id IS NULL;
CREATE UNIQUE INDEX web_sessions_session_id ON web_sessions(session_id);

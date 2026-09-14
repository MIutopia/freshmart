CREATE TABLE IF NOT EXISTS auth_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users(id),
  KEY idx_auth_session_active (user_id, expires_at, revoked_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  action_code VARCHAR(80) NOT NULL,
  resource_type VARCHAR(48) NOT NULL,
  resource_id VARCHAR(80) NULL,
  detail_json JSON NULL,
  source_ip VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  KEY idx_audit_actor_created (actor_user_id, created_at),
  KEY idx_audit_resource_created (resource_type, resource_id, created_at)
) ENGINE=InnoDB;

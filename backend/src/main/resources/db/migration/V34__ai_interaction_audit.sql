CREATE TABLE IF NOT EXISTS freshmart_log.ai_interaction_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  scenario VARCHAR(64) NOT NULL,
  subject_reference VARCHAR(80) NULL,
  model_name VARCHAR(120) NOT NULL,
  request_sha256 CHAR(64) NOT NULL,
  response_sha256 CHAR(64) NULL,
  status VARCHAR(16) NOT NULL,
  error_code VARCHAR(80) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_interaction_actor_created (actor_user_id, created_at),
  KEY idx_ai_interaction_scenario_created (scenario, created_at)
) ENGINE=InnoDB;

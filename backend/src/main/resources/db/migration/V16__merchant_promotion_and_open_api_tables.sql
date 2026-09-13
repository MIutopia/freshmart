CREATE TABLE IF NOT EXISTS freshmart_merchant.batch_promotions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  promotion_id BIGINT NULL,
  markdown_rate DECIMAL(5,2) NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_batch_promotion_active (batch_id, status, starts_at, ends_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.api_clients (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NULL,
  name VARCHAR(120) NOT NULL,
  client_key VARCHAR(64) NOT NULL UNIQUE,
  secret_hash VARCHAR(255) NOT NULL,
  scopes_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_client_merchant_status (merchant_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.api_access_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_client_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  method VARCHAR(12) NOT NULL,
  path VARCHAR(255) NOT NULL,
  response_status INT NOT NULL,
  duration_ms INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_log_client_created (api_client_id, created_at)
) ENGINE=InnoDB;

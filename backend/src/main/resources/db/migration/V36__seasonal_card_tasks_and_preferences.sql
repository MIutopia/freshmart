CREATE TABLE IF NOT EXISTS freshmart_user.user_notification_preferences (
  user_id BIGINT PRIMARY KEY,
  seasonal_card_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_log.holiday_card_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_key VARCHAR(64) NOT NULL,
  holiday_key VARCHAR(32) NOT NULL,
  greeting VARCHAR(120) NOT NULL,
  scheduled_at DATETIME NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  created_by BIGINT NOT NULL,
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  sent_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_holiday_card_task_key (task_key),
  KEY idx_holiday_card_task_due (status, scheduled_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_log.holiday_card_deliveries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  inbox_message_id BIGINT NULL,
  last_error VARCHAR(500) NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_holiday_card_delivery (task_id, user_id),
  KEY idx_holiday_card_delivery_status (task_id, status)
) ENGINE=InnoDB;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_log' AND table_name = 'inbox_messages' AND column_name = 'idempotency_key');
SET @sql = IF(@c = 0,
  'ALTER TABLE freshmart_log.inbox_messages ADD COLUMN idempotency_key VARCHAR(100) NULL',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = 'freshmart_log' AND table_name = 'inbox_messages' AND index_name = 'uk_inbox_idempotency');
SET @sql = IF(@i = 0,
  'CREATE UNIQUE INDEX uk_inbox_idempotency ON freshmart_log.inbox_messages (idempotency_key)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

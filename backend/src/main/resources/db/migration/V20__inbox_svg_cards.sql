CREATE TABLE IF NOT EXISTS freshmart_log.inbox_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  message_type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  card_svg_url VARCHAR(512) NULL,
  card_svg_content LONGTEXT NULL,
  business_type VARCHAR(32) NULL,
  business_id BIGINT NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_inbox_unread (user_id, read_at, created_at)
) ENGINE=InnoDB;

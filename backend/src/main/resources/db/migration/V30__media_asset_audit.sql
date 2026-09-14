CREATE TABLE IF NOT EXISTS freshmart_log.media_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  storage_key VARCHAR(64) NOT NULL UNIQUE,
  storage_file_name VARCHAR(100) NOT NULL UNIQUE,
  uploader_user_id BIGINT NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_media_uploader_created (uploader_user_id, created_at)
) ENGINE=InnoDB;

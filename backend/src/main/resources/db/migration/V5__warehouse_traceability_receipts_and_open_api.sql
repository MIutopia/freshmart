CREATE TABLE IF NOT EXISTS warehouses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  zone_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL UNIQUE,
  address VARCHAR(300) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_warehouse_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_warehouse_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones(id),
  UNIQUE KEY uk_warehouse_merchant_name (merchant_id, name)
) ENGINE=InnoDB;

ALTER TABLE inventory_batches
  ADD COLUMN warehouse_id BIGINT NULL AFTER product_id,
  ADD CONSTRAINT fk_batch_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  ADD KEY idx_batch_warehouse_expiry (warehouse_id, expires_on, available_grams);

ALTER TABLE orders
  ADD COLUMN warehouse_id BIGINT NULL AFTER merchant_id,
  ADD COLUMN delivery_zone_id BIGINT NULL AFTER warehouse_id,
  ADD CONSTRAINT fk_order_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  ADD CONSTRAINT fk_order_delivery_zone FOREIGN KEY (delivery_zone_id) REFERENCES delivery_zones(id),
  ADD KEY idx_order_boundary (merchant_id, warehouse_id, delivery_zone_id);

ALTER TABLE order_items
  ADD COLUMN warehouse_id BIGINT NULL AFTER product_scope,
  ADD CONSTRAINT fk_order_item_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);

CREATE TABLE IF NOT EXISTS order_item_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_item_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  allocated_grams INT NOT NULL,
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_allocation_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  CONSTRAINT fk_allocation_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
  CONSTRAINT fk_allocation_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  UNIQUE KEY uk_item_batch_allocation (order_item_id, batch_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS electronic_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  receipt_snapshot JSON NOT NULL,
  issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_receipt_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS batch_promotions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  promotion_id BIGINT NULL,
  markdown_rate DECIMAL(5,2) NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_batch_promotion_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
  CONSTRAINT fk_batch_promotion_rule FOREIGN KEY (promotion_id) REFERENCES promotion_rules(id),
  KEY idx_batch_promotion_active (batch_id, status, starts_at, ends_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_clients (
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
  CONSTRAINT fk_api_client_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_api_client_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_access_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_client_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  method VARCHAR(12) NOT NULL,
  path VARCHAR(255) NOT NULL,
  response_status INT NOT NULL,
  duration_ms INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_api_log_client FOREIGN KEY (api_client_id) REFERENCES api_clients(id),
  KEY idx_api_log_client_created (api_client_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS media_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NULL,
  uploader_user_id BIGINT NOT NULL,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  media_type VARCHAR(24) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_media_asset_size CHECK (size_bytes > 0 AND size_bytes <= 31457280),
  CONSTRAINT fk_media_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_media_uploader FOREIGN KEY (uploader_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('media.upload.max.bytes', '31457280', 'INTEGER', '图片和视频单文件上传上限为 30 MB'),
  ('capacity.daily.order.target', '50', 'INTEGER', '首期目标日订单量'),
  ('capacity.concurrent.user.target', '20', 'INTEGER', '首期目标并发用户量'),
  ('batch.near.expiry.days', '3', 'INTEGER', '临期批次自动进入低价促销候选的提前天数')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), description = VALUES(description);

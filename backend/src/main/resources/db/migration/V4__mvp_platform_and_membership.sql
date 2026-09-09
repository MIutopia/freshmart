ALTER TABLE users
  ADD COLUMN password_hash VARCHAR(255) NULL AFTER phone,
  ADD COLUMN login_name VARCHAR(64) NULL AFTER phone,
  ADD COLUMN user_type VARCHAR(24) NOT NULL DEFAULT 'CONSUMER' AFTER role,
  ADD UNIQUE KEY uk_user_login_name (login_name);

CREATE TABLE IF NOT EXISTS user_role_assignments (
  user_id BIGINT NOT NULL,
  role_code VARCHAR(24) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS platform_commission_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NULL,
  commission_rate DECIMAL(5,2) NOT NULL,
  effective_from DATETIME NOT NULL,
  effective_to DATETIME NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_commission_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
  CONSTRAINT fk_commission_operator FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB;

INSERT INTO platform_commission_rules (category_id, commission_rate, effective_from)
VALUES (NULL, 10.00, CURRENT_TIMESTAMP);

CREATE TABLE IF NOT EXISTS merchant_applications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  applicant_user_id BIGINT NOT NULL,
  business_license_url VARCHAR(512) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(500) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_application_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_application_user FOREIGN KEY (applicant_user_id) REFERENCES users(id),
  CONSTRAINT fk_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS merchant_settlements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  gross_amount DECIMAL(10,2) NOT NULL,
  commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  net_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  settled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_order_settlement (merchant_id, order_id),
  CONSTRAINT fk_settlement_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS delivery_zones (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  area_code VARCHAR(32) NOT NULL UNIQUE,
  boundary_json JSON NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS merchant_delivery_zones (
  merchant_id BIGINT NOT NULL,
  zone_id BIGINT NOT NULL,
  PRIMARY KEY (merchant_id, zone_id),
  CONSTRAINT fk_merchant_zone_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_merchant_zone_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wallet_accounts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  frozen_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  trade_id BIGINT NULL,
  transaction_type VARCHAR(24) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallet_accounts(id),
  CONSTRAINT fk_wallet_tx_trade FOREIGN KEY (trade_id) REFERENCES trade_orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS membership_levels (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL UNIQUE,
  min_points INT NOT NULL,
  discount_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_memberships (
  user_id BIGINT PRIMARY KEY,
  level_id BIGINT NOT NULL,
  points INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_membership_level FOREIGN KEY (level_id) REFERENCES membership_levels(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS points_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  trade_id BIGINT NULL,
  change_amount INT NOT NULL,
  balance_after INT NOT NULL,
  reason VARCHAR(120) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_points_trade FOREIGN KEY (trade_id) REFERENCES trade_orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS promotion_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NULL,
  promotion_type VARCHAR(24) NOT NULL,
  name VARCHAR(120) NOT NULL,
  rule_json JSON NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  stackable BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT fk_promotion_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rider_profiles (
  user_id BIGINT PRIMARY KEY,
  employee_no VARCHAR(40) NOT NULL UNIQUE,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  current_zone_id BIGINT NULL,
  CONSTRAINT fk_rider_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_rider_zone FOREIGN KEY (current_zone_id) REFERENCES delivery_zones(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rider_performance_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rider_user_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  assigned_count INT NOT NULL DEFAULT 0,
  accepted_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  timeout_count INT NOT NULL DEFAULT 0,
  on_time_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rider_stat_day (rider_user_id, stat_date),
  CONSTRAINT fk_rider_stat_user FOREIGN KEY (rider_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

ALTER TABLE delivery_tasks
  ADD COLUMN accept_deadline_at DATETIME NULL AFTER status,
  ADD COLUMN accepted_at DATETIME NULL AFTER assigned_at,
  ADD COLUMN timeout_at DATETIME NULL AFTER delivered_at;

ALTER TABLE refund_orders
  MODIFY COLUMN issue_type VARCHAR(32) NOT NULL DEFAULT 'QUALITY',
  ADD COLUMN review_mode VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_SERVICE_AI' AFTER issue_type,
  ADD COLUMN reviewed_by BIGINT NULL AFTER review_mode,
  ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by,
  ADD CONSTRAINT fk_refund_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id);

ALTER TABLE products
  ADD COLUMN merchant_price_per_kg DECIMAL(10,2) NULL AFTER market_price_per_kg,
  ADD COLUMN platform_subsidy_per_kg DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER merchant_price_per_kg;

ALTER TABLE trade_orders
  ADD COLUMN promotion_snapshot JSON NULL AFTER discount_amount;

UPDATE refund_orders
SET issue_type = 'QUALITY'
WHERE issue_type NOT IN ('OUT_OF_STOCK', 'QUALITY');

ALTER TABLE refund_orders
  ADD CONSTRAINT chk_refund_issue_type CHECK (issue_type IN ('OUT_OF_STOCK', 'QUALITY'));

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('weighing.platform.absorb.limit', '1.50', 'DECIMAL', '市场价上浮 5% 内且称重增量不超过该值时由平台承担'),
  ('product.market.price.max.markup.rate', '5.00', 'DECIMAL', '商家上架价不得超过平台市场价的上浮比例'),
  ('refund.default.window.minutes', '1440', 'INTEGER', '首期售后默认申请窗口为送达后一天')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), description = VALUES(description);

UPDATE refund_policies
SET window_minutes = 1440
WHERE product_scope IN ('FRUIT', 'VEGETABLE', 'OTHER');

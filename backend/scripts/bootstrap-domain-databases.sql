CREATE DATABASE IF NOT EXISTS freshmart_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS freshmart_merchant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS freshmart_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS freshmart_trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS freshmart_log CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_user.* TO 'freshmart'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_merchant.* TO 'freshmart'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_delivery.* TO 'freshmart'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_trade.* TO 'freshmart'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_log.* TO 'freshmart'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_user.* TO 'freshmart'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_merchant.* TO 'freshmart'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_delivery.* TO 'freshmart'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_trade.* TO 'freshmart'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE,DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart_log.* TO 'freshmart'@'127.0.0.1';
FLUSH PRIVILEGES;

CREATE TABLE IF NOT EXISTS freshmart_user.users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL UNIQUE,
  login_name VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.user_role_assignments (
  user_id BIGINT NOT NULL,
  role_code VARCHAR(24) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.auth_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_auth_session_active (user_id, expires_at, revoked_at),
  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.user_addresses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  recipient_name VARCHAR(64) NOT NULL,
  recipient_phone VARCHAR(32) NOT NULL,
  delivery_zone_id BIGINT NOT NULL,
  detail_address VARCHAR(300) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_address_user (user_id, status),
  CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.wallet_accounts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  frozen_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.wallet_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  trade_id BIGINT NULL,
  transaction_type VARCHAR(24) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_wallet_transaction_trade (trade_id),
  CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES freshmart_user.wallet_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.user_memberships (
  user_id BIGINT PRIMARY KEY,
  level_id BIGINT NOT NULL,
  points INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_membership_level (level_id),
  CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.points_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  trade_id BIGINT NULL,
  change_amount INT NOT NULL,
  balance_after INT NOT NULL,
  reason VARCHAR(120) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_points_user_created (user_id, created_at),
  CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_user.user_coupons (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
  claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_trade_id BIGINT NULL,
  used_at DATETIME NULL,
  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  KEY idx_user_coupon_status (user_id, status),
  CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES freshmart_user.users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.merchants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  service_area_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_merchant_owner (owner_user_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.merchant_applications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  applicant_user_id BIGINT NOT NULL,
  business_license_url VARCHAR(512) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(500) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_application_applicant_status (applicant_user_id, status),
  CONSTRAINT fk_application_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.product_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NULL,
  pricing_mode VARCHAR(16) NOT NULL DEFAULT 'WEIGHT',
  market_price_per_kg DECIMAL(10,2) NOT NULL,
  merchant_price_per_kg DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_product_merchant_category (merchant_id, category_id, status),
  CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES freshmart_merchant.product_categories(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.warehouses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  delivery_zone_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(40) NOT NULL UNIQUE,
  address VARCHAR(300) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_warehouse_merchant_name (merchant_id, name),
  CONSTRAINT fk_warehouse_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.inventory_batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  available_grams INT NOT NULL DEFAULT 0,
  reserved_grams INT NOT NULL DEFAULT 0,
  expires_on DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inventory_batch (product_id, warehouse_id, batch_no),
  KEY idx_batch_warehouse_expiry (warehouse_id, expires_on, available_grams),
  CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES freshmart_merchant.products(id),
  CONSTRAINT fk_batch_warehouse FOREIGN KEY (warehouse_id) REFERENCES freshmart_merchant.warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.promotion_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NULL,
  promotion_type VARCHAR(24) NOT NULL,
  name VARCHAR(120) NOT NULL,
  rule_json JSON NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  stackable BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_promotion_scope_time (merchant_id, status, starts_at, ends_at),
  CONSTRAINT fk_promotion_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.coupons (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  coupon_type VARCHAR(16) NOT NULL,
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  total_quantity INT NOT NULL,
  claimed_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_coupon_scope_time (merchant_id, status, starts_at, ends_at),
  CONSTRAINT fk_coupon_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id)
) ENGINE=InnoDB;

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
  request_nonce VARCHAR(64) NULL,
  method VARCHAR(12) NOT NULL,
  path VARCHAR(255) NOT NULL,
  response_status INT NOT NULL,
  duration_ms INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_log_client_created (api_client_id, created_at),
  UNIQUE KEY uk_api_log_client_nonce (api_client_id, request_nonce)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.membership_levels (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL UNIQUE,
  min_points INT NOT NULL,
  discount_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.warehouse_operable_categories (
  warehouse_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (warehouse_id, category_id),
  CONSTRAINT fk_warehouse_category_warehouse FOREIGN KEY (warehouse_id) REFERENCES freshmart_merchant.warehouses(id),
  CONSTRAINT fk_warehouse_category_category FOREIGN KEY (category_id) REFERENCES freshmart_merchant.product_categories(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.merchant_category_warehouse_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_warehouse_rule (merchant_id, category_id, warehouse_id),
  KEY idx_category_warehouse_rule (merchant_id, category_id, status, priority),
  CONSTRAINT fk_category_rule_merchant FOREIGN KEY (merchant_id) REFERENCES freshmart_merchant.merchants(id),
  CONSTRAINT fk_category_rule_category FOREIGN KEY (category_id) REFERENCES freshmart_merchant.product_categories(id),
  CONSTRAINT fk_category_rule_warehouse FOREIGN KEY (warehouse_id) REFERENCES freshmart_merchant.warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_delivery.delivery_zones (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  area_code VARCHAR(32) NOT NULL UNIQUE,
  boundary_json JSON NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_delivery.rider_profiles (
  user_id BIGINT PRIMARY KEY,
  employee_no VARCHAR(40) NOT NULL UNIQUE,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  current_zone_id BIGINT NULL,
  KEY idx_rider_zone (current_zone_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_delivery.delivery_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  merchant_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  delivery_zone_id BIGINT NOT NULL,
  rider_user_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'WAITING_ASSIGNMENT',
  accept_deadline_at DATETIME NULL,
  assigned_at DATETIME NULL,
  accepted_at DATETIME NULL,
  picked_at DATETIME NULL,
  delivered_at DATETIME NULL,
  proof_url VARCHAR(512) NULL,
  exception_note VARCHAR(500) NULL,
  KEY idx_delivery_rider_status (rider_user_id, status),
  KEY idx_delivery_zone_status (delivery_zone_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_delivery.rider_performance_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rider_user_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  assigned_count INT NOT NULL DEFAULT 0,
  accepted_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  timeout_count INT NOT NULL DEFAULT 0,
  on_time_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rider_stat_day (rider_user_id, stat_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.trade_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  goods_amount DECIMAL(10,2) NOT NULL,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  payable_amount DECIMAL(10,2) NOT NULL,
  reservation_expires_at DATETIME NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_trade_user_status (user_id, status),
  KEY idx_trade_expiration (status, reservation_expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  trade_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  delivery_zone_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  goods_amount DECIMAL(10,2) NOT NULL,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  payable_amount DECIMAL(10,2) NOT NULL,
  address_snapshot JSON NOT NULL,
  pricing_snapshot JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_order_trade (trade_id),
  KEY idx_order_merchant_status (merchant_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(160) NOT NULL,
  warehouse_id BIGINT NOT NULL,
  weight_grams INT NOT NULL,
  market_price_per_kg DECIMAL(10,2) NOT NULL,
  merchant_price_per_kg DECIMAL(10,2) NOT NULL,
  user_price_per_kg DECIMAL(10,2) NOT NULL,
  merchant_gross_amount DECIMAL(10,2) NOT NULL,
  user_goods_amount DECIMAL(10,2) NOT NULL,
  platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  batch_promotion_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  flash_sale_id BIGINT NULL,
  flash_sale_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_item_order (order_id),
  KEY idx_order_item_product (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_merchant.flash_sale_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sale_price_per_kg DECIMAL(10,2) NOT NULL,
  total_grams INT NOT NULL,
  reserved_grams INT NOT NULL DEFAULT 0,
  sold_grams INT NOT NULL DEFAULT 0,
  per_user_limit_grams INT NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_flash_sale_product_active (product_id, status, starts_at, ends_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.order_item_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_item_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  batch_promotion_id BIGINT NULL,
  markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0,
  allocated_grams INT NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item_batch_allocation (order_item_id, batch_id),
  KEY idx_allocation_order_item (order_item_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.electronic_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  receipt_snapshot JSON NOT NULL,
  issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.inventory_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  batch_promotion_id BIGINT NULL,
  markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  warehouse_id BIGINT NOT NULL,
  reserved_grams INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NOT NULL,
  released_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reservation_expiration (status, expires_at),
  KEY idx_reservation_trade (trade_id),
  KEY idx_reservation_order_item (order_item_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.flash_sale_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flash_sale_id BIGINT NOT NULL,
  trade_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  reserved_grams INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NOT NULL,
  released_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flash_sale_trade (flash_sale_id, trade_id),
  KEY idx_flash_sale_reservation_expiry (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.payment_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(40) NOT NULL UNIQUE,
  trade_id BIGINT NOT NULL,
  provider VARCHAR(24) NOT NULL,
  payment_mode VARCHAR(16) NOT NULL,
  provider_transaction_id VARCHAR(64) NULL UNIQUE,
  code_url VARCHAR(512) NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_payment_trade_status (trade_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.refund_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_no VARCHAR(40) NOT NULL UNIQUE,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  reason VARCHAR(300) NOT NULL,
  issue_type VARCHAR(32) NOT NULL,
  evidence_description VARCHAR(1000) NOT NULL,
  evidence_images_json JSON NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  review_mode VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_SERVICE_AI',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  refunded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_refund_order_status (order_id, status),
  CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES freshmart_trade.payment_orders(id),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES freshmart_trade.orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.fee_ledgers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_id BIGINT NULL,
  order_id BIGINT NULL,
  merchant_id BIGINT NULL,
  fee_type VARCHAR(32) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency_code CHAR(3) NOT NULL DEFAULT 'CNY',
  direction VARCHAR(8) NOT NULL,
  reference_type VARCHAR(32) NULL,
  reference_id VARCHAR(80) NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_fee_order (order_id, fee_type),
  KEY idx_fee_merchant (merchant_id, occurred_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.merchant_settlements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  gross_amount DECIMAL(10,2) NOT NULL,
  commission_base_amount DECIMAL(10,2) NOT NULL,
  platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  commission_rate DECIMAL(5,2) NOT NULL,
  commission_amount DECIMAL(10,2) NOT NULL,
  net_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  settled_at DATETIME NULL,
  reversed_at DATETIME NULL,
  reversal_reason VARCHAR(80) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_order_settlement (merchant_id, order_id),
  KEY idx_settlement_merchant_status (merchant_id, status, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.refund_inventory_dispositions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_id BIGINT NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  disposition VARCHAR(32) NOT NULL,
  reason VARCHAR(120) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_refund_inventory_order (order_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.weighing_adjustments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  prepaid_goods_amount DECIMAL(10,2) NOT NULL,
  actual_goods_amount DECIMAL(10,2) NOT NULL,
  difference_amount DECIMAL(10,2) NOT NULL,
  action VARCHAR(24) NOT NULL,
  refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  absorbed_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  note VARCHAR(500) NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  settled_at DATETIME NULL,
  settlement_reference VARCHAR(80) NULL,
  UNIQUE KEY uk_weighing_order (order_id),
  KEY idx_weighing_merchant (merchant_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_log.audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  action_code VARCHAR(80) NOT NULL,
  resource_type VARCHAR(48) NOT NULL,
  resource_id VARCHAR(80) NULL,
  detail_json JSON NULL,
  source_ip VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_actor_created (actor_user_id, created_at),
  KEY idx_audit_resource_created (resource_type, resource_id, created_at)
) ENGINE=InnoDB;

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
  KEY idx_inbox_user_read (user_id, read_at, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_log.integration_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_type VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(48) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_outbox_dispatch (status, available_at)
) ENGINE=InnoDB;

INSERT IGNORE INTO freshmart_user.users (phone, login_name, password_hash, nickname, status)
VALUES
  ('13600001001', 'admin-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '本地测试管理员', 'ACTIVE'),
  ('13800001001', 'consumer-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户01', 'ACTIVE'),
  ('13800001002', 'consumer-test-02', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户02', 'ACTIVE'),
  ('13800001003', 'consumer-test-03', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户03', 'ACTIVE'),
  ('13800001004', 'consumer-test-04', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户04', 'ACTIVE'),
  ('13800001005', 'consumer-test-05', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户05', 'ACTIVE'),
  ('13800001006', 'consumer-test-06', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户06', 'ACTIVE'),
  ('13800001007', 'consumer-test-07', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户07', 'ACTIVE'),
  ('13800001008', 'consumer-test-08', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户08', 'ACTIVE'),
  ('13800001009', 'consumer-test-09', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户09', 'ACTIVE'),
  ('13800001010', 'consumer-test-10', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户10', 'ACTIVE'),
  ('13900001001', 'merchant-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家01', 'ACTIVE'),
  ('13900001002', 'merchant-test-02', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家02', 'ACTIVE'),
  ('13900001003', 'merchant-test-03', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家03', 'ACTIVE'),
  ('13900001004', 'merchant-test-04', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家04', 'ACTIVE'),
  ('13900001005', 'merchant-test-05', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家05', 'ACTIVE'),
  ('13900001006', 'merchant-test-06', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家06', 'ACTIVE'),
  ('13900001007', 'merchant-test-07', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家07', 'ACTIVE'),
  ('13900001008', 'merchant-test-08', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家08', 'ACTIVE'),
  ('13900001009', 'merchant-test-09', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家09', 'ACTIVE'),
  ('13900001010', 'merchant-test-10', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家10', 'ACTIVE'),
  ('13700001001', 'rider-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员01', 'ACTIVE'),
  ('13700001002', 'rider-test-02', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员02', 'ACTIVE'),
  ('13700001003', 'rider-test-03', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员03', 'ACTIVE'),
  ('13700001004', 'rider-test-04', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员04', 'ACTIVE'),
  ('13700001005', 'rider-test-05', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员05', 'ACTIVE'),
  ('13700001006', 'rider-test-06', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员06', 'ACTIVE'),
  ('13700001007', 'rider-test-07', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员07', 'ACTIVE'),
  ('13700001008', 'rider-test-08', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员08', 'ACTIVE'),
  ('13700001009', 'rider-test-09', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员09', 'ACTIVE'),
  ('13700001010', 'rider-test-10', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员10', 'ACTIVE');

UPDATE freshmart_user.users
SET password_hash = '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym'
WHERE login_name LIKE 'consumer-test-%'
   OR login_name LIKE 'merchant-test-%'
   OR login_name LIKE 'rider-test-%';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'ADMIN'
FROM freshmart_user.users
WHERE login_name = 'admin-test-01';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'CONSUMER'
FROM freshmart_user.users
WHERE login_name LIKE 'consumer-test-%';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'MERCHANT'
FROM freshmart_user.users
WHERE login_name LIKE 'merchant-test-%';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'RIDER'
FROM freshmart_user.users
WHERE login_name LIKE 'rider-test-%';

INSERT INTO freshmart_merchant.merchants (owner_user_id, name, status, service_area_json)
SELECT account.id, CONCAT('本地测试生鲜商家', RIGHT(account.login_name, 2)), 'ACTIVE', JSON_OBJECT('scope', 'LOCAL_TEST')
FROM freshmart_user.users account
LEFT JOIN freshmart_merchant.merchants merchant ON merchant.owner_user_id = account.id
WHERE account.login_name LIKE 'merchant-test-%'
  AND merchant.id IS NULL;

INSERT INTO freshmart_merchant.merchant_applications (merchant_id, applicant_user_id, status, review_note, reviewed_at)
SELECT merchant.id, merchant.owner_user_id, 'APPROVED', '本地测试账号预置审核通过', CURRENT_TIMESTAMP
FROM freshmart_merchant.merchants merchant
LEFT JOIN freshmart_merchant.merchant_applications application ON application.merchant_id = merchant.id
WHERE merchant.name LIKE '本地测试生鲜商家%'
  AND application.id IS NULL;

INSERT IGNORE INTO freshmart_delivery.rider_profiles (user_id, employee_no, status)
SELECT id, CONCAT('TEST-RIDER-', RIGHT(login_name, 2)), 'ACTIVE'
FROM freshmart_user.users
WHERE login_name LIKE 'rider-test-%';

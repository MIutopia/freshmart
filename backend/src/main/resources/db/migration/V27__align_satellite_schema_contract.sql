CREATE TABLE IF NOT EXISTS freshmart_user.user_memberships (
  user_id BIGINT PRIMARY KEY,
  level_id BIGINT NOT NULL,
  points INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_membership_level (level_id)
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
  KEY idx_points_user_created (user_id, created_at)
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
  KEY idx_user_coupon_status (user_id, status)
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
  KEY idx_promotion_scope_time (merchant_id, status, starts_at, ends_at)
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
  KEY idx_coupon_scope_time (merchant_id, status, starts_at, ends_at)
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
  PRIMARY KEY (warehouse_id, category_id)
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
  KEY idx_category_warehouse_rule (merchant_id, category_id, status, priority)
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
  manual_refund_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
  manual_refund_completed_at DATETIME NULL,
  manual_refund_operator_id BIGINT NULL,
  manual_refund_failure_reason VARCHAR(300) NULL,
  review_mode VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_SERVICE_AI',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  refunded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_refund_order_status (order_id, status)
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

SET @inbox_svg_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'freshmart_log'
    AND table_name = 'inbox_messages'
    AND column_name = 'card_svg_content'
);
SET @inbox_svg_column_sql = IF(
  @inbox_svg_column_exists = 0
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'freshmart_log' AND table_name = 'inbox_messages'
    ),
  'ALTER TABLE freshmart_log.inbox_messages ADD COLUMN card_svg_content LONGTEXT NULL',
  'SELECT 1'
);
PREPARE inbox_svg_column_stmt FROM @inbox_svg_column_sql;
EXECUTE inbox_svg_column_stmt;
DEALLOCATE PREPARE inbox_svg_column_stmt;

SET @inbox_svg_url_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_log' AND table_name = 'inbox_messages' AND column_name = 'card_svg_url'
);
SET @inbox_svg_url_sql = IF(
  @inbox_svg_url_exists = 0 AND EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema = 'freshmart_log' AND table_name = 'inbox_messages'
  ),
  'ALTER TABLE freshmart_log.inbox_messages ADD COLUMN card_svg_url VARCHAR(512) NULL',
  'SELECT 1'
);
PREPARE inbox_svg_url_stmt FROM @inbox_svg_url_sql;
EXECUTE inbox_svg_url_stmt;
DEALLOCATE PREPARE inbox_svg_url_stmt;

SET @trade_orders_actual_goods_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'orders' AND column_name = 'actual_goods_amount'
);
SET @trade_orders_actual_goods_sql = IF(
  @trade_orders_actual_goods_exists = 0,
  'ALTER TABLE freshmart_trade.orders ADD COLUMN actual_goods_amount DECIMAL(10,2) NULL',
  'SELECT 1'
);
PREPARE trade_orders_actual_goods_stmt FROM @trade_orders_actual_goods_sql;
EXECUTE trade_orders_actual_goods_stmt;
DEALLOCATE PREPARE trade_orders_actual_goods_stmt;

SET @trade_orders_absorbed_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'orders' AND column_name = 'platform_absorbed_amount'
);
SET @trade_orders_absorbed_sql = IF(
  @trade_orders_absorbed_exists = 0,
  'ALTER TABLE freshmart_trade.orders ADD COLUMN platform_absorbed_amount DECIMAL(10,2) NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE trade_orders_absorbed_stmt FROM @trade_orders_absorbed_sql;
EXECUTE trade_orders_absorbed_stmt;
DEALLOCATE PREPARE trade_orders_absorbed_stmt;

SET @trade_item_warehouse_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'order_items' AND column_name = 'warehouse_id'
);
SET @trade_item_warehouse_sql = IF(
  @trade_item_warehouse_exists = 0,
  'ALTER TABLE freshmart_trade.order_items ADD COLUMN warehouse_id BIGINT NULL',
  'SELECT 1'
);
PREPARE trade_item_warehouse_stmt FROM @trade_item_warehouse_sql;
EXECUTE trade_item_warehouse_stmt;
DEALLOCATE PREPARE trade_item_warehouse_stmt;

SET @trade_item_pricing_sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE freshmart_trade.order_items ADD COLUMN merchant_price_per_kg DECIMAL(10,2) NULL, ADD COLUMN user_price_per_kg DECIMAL(10,2) NULL, ADD COLUMN merchant_gross_amount DECIMAL(10,2) NULL, ADD COLUMN user_goods_amount DECIMAL(10,2) NULL, ADD COLUMN platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0, ADD COLUMN batch_promotion_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0, ADD COLUMN flash_sale_id BIGINT NULL, ADD COLUMN flash_sale_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'order_items' AND column_name = 'merchant_price_per_kg'
);
PREPARE trade_item_pricing_stmt FROM @trade_item_pricing_sql;
EXECUTE trade_item_pricing_stmt;
DEALLOCATE PREPARE trade_item_pricing_stmt;

SET @reconciliation_table_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = 'freshmart_trade'
    AND table_name = 'payment_reconciliation_differences'
);
SET @reconciliation_status_update_sql = IF(
  @reconciliation_table_exists = 1,
  'UPDATE freshmart_trade.payment_reconciliation_differences SET status = CASE status WHEN ''OPEN'' THEN ''UNHANDLED'' WHEN ''RESOLVED'' THEN ''HANDLED'' ELSE status END WHERE status IN (''OPEN'', ''RESOLVED'')',
  'SELECT 1'
);
PREPARE reconciliation_status_update_stmt FROM @reconciliation_status_update_sql;
EXECUTE reconciliation_status_update_stmt;
DEALLOCATE PREPARE reconciliation_status_update_stmt;

SET @reconciliation_status_default_sql = IF(
  @reconciliation_table_exists = 1,
  'ALTER TABLE freshmart_trade.payment_reconciliation_differences MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT ''UNHANDLED''',
  'SELECT 1'
);
PREPARE reconciliation_status_default_stmt FROM @reconciliation_status_default_sql;
EXECUTE reconciliation_status_default_stmt;
DEALLOCATE PREPARE reconciliation_status_default_stmt;

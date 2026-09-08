CREATE TABLE IF NOT EXISTS trade_orders (
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
  CONSTRAINT fk_trade_user FOREIGN KEY (user_id) REFERENCES users(id),
  KEY idx_trade_expiration (status, reservation_expires_at)
) ENGINE=InnoDB;

ALTER TABLE orders
  ADD COLUMN trade_id BIGINT NULL AFTER order_no,
  ADD COLUMN actual_goods_amount DECIMAL(10,2) NULL AFTER goods_amount,
  ADD COLUMN platform_absorbed_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER discount_amount,
  ADD CONSTRAINT fk_order_trade FOREIGN KEY (trade_id) REFERENCES trade_orders(id),
  ADD KEY idx_order_trade (trade_id);

CREATE TABLE IF NOT EXISTS inventory_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  reserved_grams INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  expires_at DATETIME NOT NULL,
  released_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_reservation_trade FOREIGN KEY (trade_id) REFERENCES trade_orders(id),
  CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_reservation_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
  KEY idx_reservation_expiration (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payment_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(40) NOT NULL UNIQUE,
  trade_id BIGINT NOT NULL,
  provider VARCHAR(24) NOT NULL DEFAULT 'WECHAT_PAY',
  payment_mode VARCHAR(16) NOT NULL DEFAULT 'NATIVE',
  provider_transaction_id VARCHAR(64) NULL UNIQUE,
  code_url VARCHAR(512) NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_trade FOREIGN KEY (trade_id) REFERENCES trade_orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refund_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_no VARCHAR(40) NOT NULL UNIQUE,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  provider_refund_id VARCHAR(64) NULL UNIQUE,
  reason VARCHAR(300) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  refunded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment_orders(id),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_coupons (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  coupon_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
  claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_trade_id BIGINT NULL,
  used_at DATETIME NULL,
  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
  CONSTRAINT fk_user_coupon_trade FOREIGN KEY (used_trade_id) REFERENCES trade_orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inbox_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  message_type VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  card_svg_url VARCHAR(512) NULL,
  business_type VARCHAR(32) NULL,
  business_id BIGINT NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_inbox_user FOREIGN KEY (user_id) REFERENCES users(id),
  KEY idx_inbox_unread (user_id, read_at, created_at)
) ENGINE=InnoDB;

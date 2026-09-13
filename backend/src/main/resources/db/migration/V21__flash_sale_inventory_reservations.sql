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

ALTER TABLE freshmart.order_items
  ADD COLUMN flash_sale_id BIGINT NULL AFTER batch_promotion_discount_amount,
  ADD COLUMN flash_sale_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER flash_sale_id;

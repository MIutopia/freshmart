CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL UNIQUE,
  nickname VARCHAR(64) NOT NULL,
  role VARCHAR(24) NOT NULL DEFAULT 'USER',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS merchants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  service_area_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_merchant_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NULL,
  pricing_mode VARCHAR(16) NOT NULL DEFAULT 'WEIGHT',
  market_price_per_kg DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_categories(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  available_grams INT NOT NULL DEFAULT 0,
  reserved_grams INT NOT NULL DEFAULT 0,
  expires_on DATE NULL,
  UNIQUE KEY uk_inventory_batch (product_id, batch_no),
  CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS coupons (
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
  CONSTRAINT fk_coupon_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
  goods_amount DECIMAL(10,2) NOT NULL,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  payable_amount DECIMAL(10,2) NOT NULL,
  total_weight_grams INT NOT NULL DEFAULT 0,
  address_snapshot JSON NOT NULL,
  pricing_snapshot JSON NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_order_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(160) NOT NULL,
  weight_grams INT NOT NULL,
  market_price_per_kg DECIMAL(10,2) NOT NULL,
  line_amount DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS delivery_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  rider_user_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'WAITING_ASSIGNMENT',
  assigned_at DATETIME NULL,
  picked_at DATETIME NULL,
  delivered_at DATETIME NULL,
  proof_url VARCHAR(512) NULL,
  exception_note VARCHAR(500) NULL,
  CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_delivery_rider FOREIGN KEY (rider_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_recommendation_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  merchant_id BIGINT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  season_key VARCHAR(32) NULL,
  holiday_key VARCHAR(32) NULL,
  recommendation_json JSON NOT NULL,
  card_svg_url VARCHAR(512) NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_status_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  operator_user_id BIGINT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_status_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

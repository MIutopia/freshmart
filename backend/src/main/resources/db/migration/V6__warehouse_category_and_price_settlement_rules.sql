CREATE TABLE IF NOT EXISTS warehouse_operable_categories (
  warehouse_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (warehouse_id, category_id),
  CONSTRAINT fk_warehouse_category_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT fk_warehouse_category_category FOREIGN KEY (category_id) REFERENCES product_categories(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS merchant_category_warehouse_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_category_rule_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  CONSTRAINT fk_category_rule_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
  CONSTRAINT fk_category_rule_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  UNIQUE KEY uk_category_warehouse_rule (merchant_id, category_id, warehouse_id),
  KEY idx_category_warehouse_default (merchant_id, category_id, status, priority)
) ENGINE=InnoDB;

ALTER TABLE order_items
  ADD COLUMN merchant_price_per_kg_snapshot DECIMAL(10,2) NULL AFTER market_price_per_kg,
  ADD COLUMN user_price_per_kg_snapshot DECIMAL(10,2) NULL AFTER merchant_price_per_kg_snapshot,
  ADD COLUMN platform_price_subsidy_per_kg DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER user_price_per_kg_snapshot,
  ADD COLUMN merchant_gross_amount DECIMAL(10,2) NULL AFTER line_amount,
  ADD COLUMN platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER merchant_gross_amount,
  ADD COLUMN merchant_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER platform_price_subsidy_amount,
  ADD COLUMN platform_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER merchant_discount_amount;

ALTER TABLE merchant_settlements
  ADD COLUMN commission_base_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER gross_amount,
  ADD COLUMN platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER commission_base_amount;

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('product.market.price.max.markup.rate', '5.00', 'DECIMAL', '商家价格不得高于市场价的比例上限'),
  ('price.subsidy.user.price.cap', 'MARKET_PRICE', 'ENUM', '用户标准价封顶为市场价，商家价高出的合规部分由平台补贴给商家'),
  ('commission.exclude.platform.subsidy', 'true', 'BOOLEAN', '平台价格补贴不纳入平台佣金计提基数'),
  ('warehouse.category.rule.required', 'true', 'BOOLEAN', '批次入库和订单选仓必须匹配仓库可经营分类与商家分类仓配规则')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), description = VALUES(description);

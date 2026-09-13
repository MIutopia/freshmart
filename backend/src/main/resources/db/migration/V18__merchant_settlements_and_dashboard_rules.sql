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
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_order_settlement (merchant_id, order_id),
  KEY idx_settlement_merchant_status (merchant_id, status, created_at)
) ENGINE=InnoDB;

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES ('inventory.warning.threshold.grams', '2000', 'INTEGER', '商家经营看板低库存预警阈值，单位克')
ON DUPLICATE KEY UPDATE description = VALUES(description);

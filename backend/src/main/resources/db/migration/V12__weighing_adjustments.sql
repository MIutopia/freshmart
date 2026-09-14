CREATE TABLE IF NOT EXISTS weighing_adjustments (
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
  UNIQUE KEY uk_weighing_order (order_id)
) ENGINE=InnoDB;

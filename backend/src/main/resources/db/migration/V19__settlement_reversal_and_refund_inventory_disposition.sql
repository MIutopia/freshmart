ALTER TABLE freshmart_trade.merchant_settlements
  ADD COLUMN reversed_at DATETIME NULL AFTER settled_at,
  ADD COLUMN reversal_reason VARCHAR(80) NULL AFTER reversed_at;

CREATE TABLE IF NOT EXISTS freshmart_trade.refund_inventory_dispositions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_id BIGINT NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  disposition VARCHAR(32) NOT NULL,
  reason VARCHAR(120) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_refund_inventory_order (order_id, created_at)
) ENGINE=InnoDB;

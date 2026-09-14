CREATE TABLE IF NOT EXISTS order_item_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_item_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  allocated_grams INT NOT NULL,
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item_batch_allocation (order_item_id, batch_id),
  KEY idx_allocation_order_item (order_item_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS electronic_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  receipt_snapshot JSON NOT NULL,
  issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

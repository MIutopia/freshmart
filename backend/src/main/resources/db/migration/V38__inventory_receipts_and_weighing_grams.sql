-- 称收入库：仓库收货必须以实际称重克数入库，并保留净重/毛重/皮重与操作人，便于追溯账实差异。
CREATE TABLE IF NOT EXISTS freshmart_merchant.inventory_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_no VARCHAR(48) NOT NULL,
  batch_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  received_grams INT NOT NULL,
  gross_grams INT NULL,
  tare_grams INT NULL,
  note VARCHAR(300) NULL,
  idempotency_key VARCHAR(96) NOT NULL,
  received_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inventory_receipt_no (receipt_no),
  UNIQUE KEY uk_inventory_receipt_idem (idempotency_key),
  KEY idx_receipt_batch (batch_id),
  KEY idx_receipt_warehouse (warehouse_id, created_at)
) ENGINE=InnoDB;

-- 称重调整补充实物克数：下单按预估克数占用并扣减库存，实际到货称重后必须把克数差额回补到批次，
-- 否则批次账实会长期偏离。REFUND_USER / PLATFORM_ABSORB 只处理金额，克数差额由这里单独留痕。
ALTER TABLE freshmart_trade.weighing_adjustments
  ADD COLUMN prepaid_grams INT NULL AFTER actual_goods_amount,
  ADD COLUMN actual_grams INT NULL AFTER prepaid_grams,
  ADD COLUMN inventory_adjust_grams INT NOT NULL DEFAULT 0 AFTER actual_grams,
  ADD COLUMN inventory_adjusted_at DATETIME NULL AFTER inventory_adjust_grams;

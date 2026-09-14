-- 逐项称重：生鲜按品类分拣，同一订单的不同商品实际重量与预估的偏差方向可能相反，
-- 因此需要把实际重量落到订单项粒度，而不是只记一个整单结果。
ALTER TABLE freshmart_trade.order_items
  ADD COLUMN actual_weight_grams INT NULL AFTER weight_grams,
  ADD COLUMN actual_goods_amount DECIMAL(10,2) NULL AFTER user_goods_amount,
  ADD COLUMN weighed_at DATETIME NULL AFTER actual_goods_amount;

-- 逐项称重明细：一次称重提交为每个订单项留一条原始记录，包含项级克数差额与库存回补克数，
-- 便于在不重算整体结算的前提下核对单品账实。
CREATE TABLE IF NOT EXISTS freshmart_trade.order_item_weighings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  adjustment_id VARCHAR(96) NOT NULL,
  prepaid_grams INT NOT NULL,
  actual_grams INT NOT NULL,
  prepaid_goods_amount DECIMAL(10,2) NOT NULL,
  actual_goods_amount DECIMAL(10,2) NOT NULL,
  inventory_adjust_grams INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item_weighing (order_item_id),
  KEY idx_item_weighing_order (order_id),
  KEY idx_item_weighing_adjustment (adjustment_id)
) ENGINE=InnoDB;

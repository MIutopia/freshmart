ALTER TABLE freshmart_trade.order_items
  ADD COLUMN IF NOT EXISTS batch_promotion_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0
    AFTER platform_price_subsidy_amount;

ALTER TABLE freshmart_trade.inventory_reservations
  ADD COLUMN IF NOT EXISTS batch_promotion_id BIGINT NULL AFTER batch_id,
  ADD COLUMN IF NOT EXISTS markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER batch_promotion_id,
  ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER markdown_rate_snapshot;

ALTER TABLE freshmart_trade.order_item_batch_allocations
  ADD COLUMN IF NOT EXISTS batch_promotion_id BIGINT NULL AFTER batch_id,
  ADD COLUMN IF NOT EXISTS markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER batch_promotion_id,
  ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER markdown_rate_snapshot;

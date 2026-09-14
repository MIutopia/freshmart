ALTER TABLE order_items
  ADD COLUMN batch_promotion_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0
    AFTER platform_price_subsidy_amount;

ALTER TABLE inventory_reservations
  ADD COLUMN batch_promotion_id BIGINT NULL AFTER batch_id,
  ADD COLUMN markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER batch_promotion_id,
  ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER markdown_rate_snapshot;

ALTER TABLE order_item_batch_allocations
  ADD COLUMN batch_promotion_id BIGINT NULL AFTER batch_id,
  ADD COLUMN markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER batch_promotion_id,
  ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER markdown_rate_snapshot;

ALTER TABLE inventory_reservations
  ADD COLUMN IF NOT EXISTS order_id BIGINT NULL AFTER trade_id,
  ADD COLUMN IF NOT EXISTS order_item_id BIGINT NULL AFTER order_id,
  ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NULL AFTER batch_id,
  ADD KEY IF NOT EXISTS idx_reservation_order_item (order_item_id);

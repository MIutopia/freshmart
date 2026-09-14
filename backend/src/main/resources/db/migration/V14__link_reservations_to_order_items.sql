SET @reservation_schema = DATABASE();

SET @order_id_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @reservation_schema
    AND table_name = 'inventory_reservations'
    AND column_name = 'order_id'
);
SET @order_id_sql = IF(
  @order_id_exists = 0,
  'ALTER TABLE inventory_reservations ADD COLUMN order_id BIGINT NULL AFTER trade_id',
  'SELECT 1'
);
PREPARE order_id_stmt FROM @order_id_sql;
EXECUTE order_id_stmt;
DEALLOCATE PREPARE order_id_stmt;

SET @order_item_id_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @reservation_schema
    AND table_name = 'inventory_reservations'
    AND column_name = 'order_item_id'
);
SET @order_item_id_sql = IF(
  @order_item_id_exists = 0,
  'ALTER TABLE inventory_reservations ADD COLUMN order_item_id BIGINT NULL AFTER order_id',
  'SELECT 1'
);
PREPARE order_item_id_stmt FROM @order_item_id_sql;
EXECUTE order_item_id_stmt;
DEALLOCATE PREPARE order_item_id_stmt;

SET @warehouse_id_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @reservation_schema
    AND table_name = 'inventory_reservations'
    AND column_name = 'warehouse_id'
);
SET @warehouse_id_sql = IF(
  @warehouse_id_exists = 0,
  'ALTER TABLE inventory_reservations ADD COLUMN warehouse_id BIGINT NULL AFTER batch_id',
  'SELECT 1'
);
PREPARE warehouse_id_stmt FROM @warehouse_id_sql;
EXECUTE warehouse_id_stmt;
DEALLOCATE PREPARE warehouse_id_stmt;

SET @reservation_order_item_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @reservation_schema
    AND table_name = 'inventory_reservations'
    AND index_name = 'idx_reservation_order_item'
);
SET @reservation_order_item_index_sql = IF(
  @reservation_order_item_index_exists = 0,
  'ALTER TABLE inventory_reservations ADD KEY idx_reservation_order_item (order_item_id)',
  'SELECT 1'
);
PREPARE reservation_order_item_index_stmt FROM @reservation_order_item_index_sql;
EXECUTE reservation_order_item_index_stmt;
DEALLOCATE PREPARE reservation_order_item_index_stmt;

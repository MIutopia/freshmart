SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'freshmart_delivery'
    AND table_name = 'delivery_tasks'
    AND column_name = 'timeout_at'
);
SET @sql = IF(
  @col_exists = 0,
  'ALTER TABLE freshmart_delivery.delivery_tasks ADD COLUMN timeout_at DATETIME NULL AFTER delivered_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

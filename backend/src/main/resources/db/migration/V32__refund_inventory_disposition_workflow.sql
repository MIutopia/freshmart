SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='freshmart_trade' AND table_name='refund_inventory_dispositions' AND column_name='processed_by');
SET @sql = IF(@c=0, 'ALTER TABLE freshmart_trade.refund_inventory_dispositions ADD COLUMN processed_by BIGINT NULL, ADD COLUMN processed_at DATETIME NULL, ADD COLUMN processing_note VARCHAR(500) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

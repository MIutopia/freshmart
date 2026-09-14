SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='freshmart_trade' AND table_name='merchant_settlements' AND column_name='settled_by');
SET @sql = IF(@c=0, 'ALTER TABLE freshmart_trade.merchant_settlements ADD COLUMN settled_by BIGINT NULL, ADD COLUMN settlement_note VARCHAR(500) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

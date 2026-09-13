-- V2 may already have been applied before the personal-payment adapter was introduced.
-- Keep this schema repair repeatable for existing local development databases.
SET @payment_code_url_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade'
    AND table_name = 'payment_orders'
    AND column_name = 'code_url'
);
SET @payment_code_url_sql = IF(
  @payment_code_url_exists = 0
    AND EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = 'freshmart_trade'
        AND table_name = 'payment_orders'
    ),
  'ALTER TABLE freshmart_trade.payment_orders ADD COLUMN code_url VARCHAR(512) NULL AFTER provider_transaction_id',
  'SELECT 1'
);
PREPARE payment_code_url_stmt FROM @payment_code_url_sql;
EXECUTE payment_code_url_stmt;
DEALLOCATE PREPARE payment_code_url_stmt;

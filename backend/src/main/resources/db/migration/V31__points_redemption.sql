CREATE TABLE IF NOT EXISTS freshmart_user.user_point_accounts (
  user_id BIGINT PRIMARY KEY,
  available_points INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_point_account_nonnegative CHECK (available_points >= 0)
) ENGINE=InnoDB;

INSERT INTO freshmart_user.user_point_accounts (user_id, available_points)
SELECT user.id, COALESCE((
  SELECT transaction.balance_after
  FROM freshmart_user.points_transactions transaction
  WHERE transaction.user_id = user.id
  ORDER BY transaction.id DESC
  LIMIT 1
), 0)
FROM freshmart_user.users user
ON DUPLICATE KEY UPDATE available_points = VALUES(available_points);

SET @trade_redeemed_points_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'trade_orders' AND column_name = 'redeemed_points'
);
SET @trade_redeemed_points_sql = IF(
  @trade_redeemed_points_exists = 0,
  'ALTER TABLE freshmart_trade.trade_orders ADD COLUMN redeemed_points INT NOT NULL DEFAULT 0 AFTER discount_amount, ADD COLUMN points_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER redeemed_points',
  'SELECT 1'
);
PREPARE trade_redeemed_points_stmt FROM @trade_redeemed_points_sql;
EXECUTE trade_redeemed_points_stmt;
DEALLOCATE PREPARE trade_redeemed_points_stmt;

SET @order_redeemed_points_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'freshmart_trade' AND table_name = 'orders' AND column_name = 'redeemed_points'
);
SET @order_redeemed_points_sql = IF(
  @order_redeemed_points_exists = 0,
  'ALTER TABLE freshmart_trade.orders ADD COLUMN redeemed_points INT NOT NULL DEFAULT 0 AFTER discount_amount, ADD COLUMN points_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER redeemed_points',
  'SELECT 1'
);
PREPARE order_redeemed_points_stmt FROM @order_redeemed_points_sql;
EXECUTE order_redeemed_points_stmt;
DEALLOCATE PREPARE order_redeemed_points_stmt;

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('points.redeem.per.currency', '1000', 'INTEGER', '积分抵扣兑换比例：1000 积分抵扣 1 元商品金额'),
  ('points.redeem.max.rate', '3.00', 'DECIMAL', '积分抵扣单笔最高商品金额比例')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), value_type = VALUES(value_type), description = VALUES(description);

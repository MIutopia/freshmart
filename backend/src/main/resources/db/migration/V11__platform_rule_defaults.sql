INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('points.per.currency', '1.00', 'DECIMAL', '支付成功后每 1 元应付金额对应的积分倍率'),
  ('commission.default.rate', '10.00', 'DECIMAL', '平台默认佣金比例')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), description = VALUES(description);

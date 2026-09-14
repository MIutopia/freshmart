INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES ('delivery.accept.timeout.minutes', '5', 'INTEGER', '配送员接单超时时长，超时后任务自动回到待派单')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), value_type = VALUES(value_type), description = VALUES(description);

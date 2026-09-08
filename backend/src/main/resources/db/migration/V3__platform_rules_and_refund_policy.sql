CREATE TABLE IF NOT EXISTS platform_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_key VARCHAR(64) NOT NULL UNIQUE,
  rule_value VARCHAR(128) NOT NULL,
  value_type VARCHAR(16) NOT NULL,
  description VARCHAR(300) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_platform_rule_operator FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB;

INSERT INTO platform_rules (rule_key, rule_value, value_type, description)
VALUES
  ('freight.free.threshold', '59.00', 'DECIMAL', '优惠后商品金额达到该值免配送费'),
  ('freight.standard.fee', '6.00', 'DECIMAL', '未达到免配送费门槛时的标准配送费'),
  ('inventory.reservation.minutes', '15', 'INTEGER', '未支付订单库存预占时长'),
  ('weighing.platform.absorb.limit', '1.50', 'DECIMAL', '称重增量不超过该值时由平台承担'),
  ('refund.fruit.window.minutes', '60', 'INTEGER', '水果送达后的售后申请窗口'),
  ('refund.vegetable.window.minutes', '4320', 'INTEGER', '蔬菜送达后的售后申请窗口')
ON DUPLICATE KEY UPDATE rule_key = VALUES(rule_key);

CREATE TABLE IF NOT EXISTS refund_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_scope VARCHAR(24) NOT NULL UNIQUE,
  window_minutes INT NOT NULL,
  evidence_required VARCHAR(32) NOT NULL DEFAULT 'IMAGE_AND_DESCRIPTION',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_refund_policy_operator FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB;

INSERT INTO refund_policies (product_scope, window_minutes, evidence_required)
VALUES
  ('FRUIT', 60, 'IMAGE_AND_DESCRIPTION'),
  ('VEGETABLE', 4320, 'IMAGE_AND_DESCRIPTION'),
  ('OTHER', 0, 'IMAGE_AND_DESCRIPTION')
ON DUPLICATE KEY UPDATE product_scope = VALUES(product_scope);

ALTER TABLE order_items
  ADD COLUMN product_scope VARCHAR(24) NOT NULL DEFAULT 'OTHER' AFTER product_id;

ALTER TABLE refund_orders
  ADD COLUMN issue_type VARCHAR(32) NOT NULL DEFAULT 'OTHER' AFTER reason,
  ADD COLUMN evidence_description VARCHAR(1000) NOT NULL AFTER issue_type,
  ADD COLUMN evidence_images_json JSON NOT NULL AFTER evidence_description,
  ADD COLUMN requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status;

ALTER TABLE delivery_tasks
  ADD COLUMN merchant_id BIGINT NULL AFTER order_id,
  ADD KEY idx_delivery_merchant_status (merchant_id, status),
  ADD CONSTRAINT fk_delivery_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id);

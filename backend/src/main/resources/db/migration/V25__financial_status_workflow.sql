UPDATE freshmart_trade.payment_orders
SET status = CASE status
  WHEN 'EXPIRED' THEN 'CANCELLED'
  WHEN 'AMOUNT_MISMATCH' THEN 'ABNORMAL'
  WHEN 'REMARK_MISSING' THEN 'ABNORMAL'
  WHEN 'VERIFICATION_FAILED' THEN 'ABNORMAL'
  ELSE status
END
WHERE status IN ('EXPIRED', 'AMOUNT_MISMATCH', 'REMARK_MISSING', 'VERIFICATION_FAILED');

UPDATE freshmart.refund_orders
SET status = CASE status
  WHEN 'PENDING_MANUAL_REFUND' THEN 'MANUAL_PROCESS'
  WHEN 'MANUAL_REFUND_COMPLETED' THEN 'REFUND_SUCCESS'
  WHEN 'MANUAL_REFUND_FAILED' THEN 'REFUND_FAIL'
  WHEN 'REFUNDED' THEN 'REFUND_SUCCESS'
  ELSE status
END
WHERE status IN ('PENDING_MANUAL_REFUND', 'MANUAL_REFUND_COMPLETED', 'MANUAL_REFUND_FAILED', 'REFUNDED');

UPDATE freshmart_trade.payment_reconciliation_differences
SET status = CASE status
  WHEN 'OPEN' THEN 'UNHANDLED'
  WHEN 'RESOLVED' THEN 'HANDLED'
  ELSE status
END
WHERE status IN ('OPEN', 'RESOLVED');

ALTER TABLE freshmart_trade.payment_reconciliation_differences
  ADD COLUMN claimed_by BIGINT NULL AFTER status,
  ADD COLUMN claimed_at DATETIME NULL AFTER claimed_by,
  ADD COLUMN resolution VARCHAR(24) NULL AFTER resolved_at,
  ADD COLUMN resolution_note VARCHAR(500) NULL AFTER resolution;

CREATE TABLE IF NOT EXISTS freshmart_trade.financial_status_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_type VARCHAR(24) NOT NULL,
  entity_no VARCHAR(80) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  action_code VARCHAR(64) NOT NULL,
  operator_user_id BIGINT NULL,
  remark VARCHAR(500) NULL,
  source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_financial_status_entity (entity_type, entity_no, created_at)
) ENGINE=InnoDB;

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'OPERATIONS' FROM freshmart_user.users WHERE login_name = 'admin-test-01';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'FINANCE' FROM freshmart_user.users WHERE login_name = 'admin-test-01';

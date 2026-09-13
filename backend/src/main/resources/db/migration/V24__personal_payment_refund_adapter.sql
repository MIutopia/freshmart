ALTER TABLE freshmart_trade.payment_orders
  ADD COLUMN IF NOT EXISTS remark_text VARCHAR(120) NULL,
  ADD COLUMN IF NOT EXISTS payment_proof_url VARCHAR(512) NULL,
  ADD COLUMN IF NOT EXISTS verified_by BIGINT NULL,
  ADD COLUMN IF NOT EXISTS verified_at DATETIME NULL,
  ADD COLUMN IF NOT EXISTS failure_code VARCHAR(48) NULL,
  ADD COLUMN IF NOT EXISTS failure_message VARCHAR(300) NULL;

CREATE TABLE IF NOT EXISTS freshmart_trade.payment_verifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(40) NOT NULL,
  verification_type VARCHAR(32) NOT NULL,
  proof_url VARCHAR(512) NULL,
  bill_transaction_id VARCHAR(128) NULL,
  bill_amount DECIMAL(10,2) NULL,
  remark_text VARCHAR(120) NULL,
  result VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  failure_message VARCHAR(300) NULL,
  verified_by BIGINT NULL,
  verified_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_payment_verification_payment (payment_no, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.payment_bill_imports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_name VARCHAR(255) NOT NULL,
  imported_by BIGINT NOT NULL,
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total_entries INT NOT NULL DEFAULT 0,
  matched_entries INT NOT NULL DEFAULT 0,
  difference_entries INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.payment_bill_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  import_id BIGINT NOT NULL,
  transaction_id VARCHAR(128) NOT NULL,
  transaction_time DATETIME NULL,
  remark_text VARCHAR(300) NULL,
  amount DECIMAL(10,2) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  payment_no VARCHAR(40) NULL,
  match_result VARCHAR(24) NOT NULL DEFAULT 'UNMATCHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_bill_entry_import FOREIGN KEY (import_id) REFERENCES payment_bill_imports(id),
  KEY idx_bill_entry_match (match_result, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS freshmart_trade.payment_reconciliation_differences (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(40) NULL,
  bill_entry_id BIGINT NULL,
  difference_type VARCHAR(32) NOT NULL,
  description VARCHAR(500) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  resolved_by BIGINT NULL,
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reconciliation_status (status, created_at)
) ENGINE=InnoDB;

ALTER TABLE freshmart_trade.refund_orders
  ADD COLUMN IF NOT EXISTS manual_refund_status VARCHAR(24) NULL,
  ADD COLUMN IF NOT EXISTS manual_refund_completed_at DATETIME NULL,
  ADD COLUMN IF NOT EXISTS manual_refund_operator_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS manual_refund_failure_reason VARCHAR(300) NULL;

ALTER TABLE freshmart_trade.weighing_adjustments
  ADD COLUMN IF NOT EXISTS settled_at DATETIME NULL AFTER created_at,
  ADD COLUMN IF NOT EXISTS settlement_reference VARCHAR(80) NULL AFTER settled_at;

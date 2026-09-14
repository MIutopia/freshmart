ALTER TABLE freshmart.weighing_adjustments
  ADD COLUMN settled_at DATETIME NULL AFTER created_at,
  ADD COLUMN settlement_reference VARCHAR(80) NULL AFTER settled_at;

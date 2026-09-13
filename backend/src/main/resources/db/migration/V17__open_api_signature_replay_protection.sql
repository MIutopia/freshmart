ALTER TABLE freshmart_merchant.api_access_logs
  ADD COLUMN IF NOT EXISTS request_nonce VARCHAR(64) NULL AFTER request_id,
  ADD UNIQUE KEY uk_api_log_client_nonce (api_client_id, request_nonce);

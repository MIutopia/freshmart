-- 补齐交易域剩余表的字段中文注释（小票、资金状态、秒杀预占、批次分配、账单导入与对账、支付核验）。
-- 仍以 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列类型、可空性、默认值与索引。

-- ===== 电子小票 freshmart_trade.electronic_receipts =====
ALTER TABLE freshmart_trade.electronic_receipts
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单，每个子订单一张小票',
  MODIFY COLUMN receipt_no VARCHAR(40) NOT NULL COMMENT '小票编号',
  MODIFY COLUMN receipt_snapshot JSON NOT NULL COMMENT '结算明细快照：商品、优惠、运费与应付金额',
  MODIFY COLUMN issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开具时间';

-- ===== 资金状态日志 freshmart_trade.financial_status_logs =====
ALTER TABLE freshmart_trade.financial_status_logs
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN entity_type VARCHAR(24) NOT NULL COMMENT '实体类型：支付、退款、退款库存处置等',
  MODIFY COLUMN entity_no VARCHAR(80) NOT NULL COMMENT '实体业务编号',
  MODIFY COLUMN from_status VARCHAR(32) NULL COMMENT '变更前状态',
  MODIFY COLUMN to_status VARCHAR(32) NOT NULL COMMENT '变更后状态',
  MODIFY COLUMN action_code VARCHAR(64) NOT NULL COMMENT '触发动作编码',
  MODIFY COLUMN operator_user_id BIGINT NULL COMMENT '操作人，系统触发时为空',
  MODIFY COLUMN remark VARCHAR(500) NULL COMMENT '备注',
  MODIFY COLUMN source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL' COMMENT '触发来源：人工或系统',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间';

-- ===== 秒杀库存预占 freshmart_trade.flash_sale_reservations =====
ALTER TABLE freshmart_trade.flash_sale_reservations
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN flash_sale_id BIGINT NOT NULL COMMENT '秒杀活动',
  MODIFY COLUMN trade_id BIGINT NOT NULL COMMENT '关联交易单',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '下单用户',
  MODIFY COLUMN reserved_grams INT NOT NULL COMMENT '预占克数',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '预占状态：生效中、已核销、已释放',
  MODIFY COLUMN expires_at DATETIME NOT NULL COMMENT '预占失效时间',
  MODIFY COLUMN released_at DATETIME NULL COMMENT '释放时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 订单项批次分配 freshmart_trade.order_item_batch_allocations =====
ALTER TABLE freshmart_trade.order_item_batch_allocations
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_item_id BIGINT NOT NULL COMMENT '关联订单项',
  MODIFY COLUMN batch_id BIGINT NOT NULL COMMENT '出库批次',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '所属仓库',
  MODIFY COLUMN batch_promotion_id BIGINT NULL COMMENT '命中的临期批次促销',
  MODIFY COLUMN markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '促销折扣率快照',
  MODIFY COLUMN allocated_grams INT NOT NULL COMMENT '该批次分配的克数，也是退货回库与批次对账的依据',
  MODIFY COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '批次折扣金额',
  MODIFY COLUMN allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间';

-- ===== 微信账单导入批次 freshmart_trade.payment_bill_imports =====
ALTER TABLE freshmart_trade.payment_bill_imports
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN file_name VARCHAR(255) NOT NULL COMMENT '导入的账单文件名',
  MODIFY COLUMN imported_by BIGINT NOT NULL COMMENT '导入人',
  MODIFY COLUMN imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  MODIFY COLUMN total_entries INT NOT NULL DEFAULT 0 COMMENT '账单总条数',
  MODIFY COLUMN matched_entries INT NOT NULL DEFAULT 0 COMMENT '匹配成功的条数',
  MODIFY COLUMN difference_entries INT NOT NULL DEFAULT 0 COMMENT '产生差异的条数';

-- ===== 微信账单流水 freshmart_trade.payment_bill_entries =====
ALTER TABLE freshmart_trade.payment_bill_entries
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN import_id BIGINT NOT NULL COMMENT '所属导入批次',
  MODIFY COLUMN transaction_id VARCHAR(128) NOT NULL COMMENT '微信交易号，用于幂等去重',
  MODIFY COLUMN transaction_time DATETIME NULL COMMENT '交易发生时间',
  MODIFY COLUMN remark_text VARCHAR(300) NULL COMMENT '账单备注，通常为交易单号',
  MODIFY COLUMN amount DECIMAL(10,2) NOT NULL COMMENT '交易金额',
  MODIFY COLUMN direction VARCHAR(16) NOT NULL COMMENT '资金方向：收入或支出',
  MODIFY COLUMN payment_no VARCHAR(40) NULL COMMENT '匹配到的支付单号',
  MODIFY COLUMN match_result VARCHAR(24) NOT NULL DEFAULT 'UNMATCHED' COMMENT '匹配结果：未匹配、已匹配等',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 支付对账差异 freshmart_trade.payment_reconciliation_differences =====
ALTER TABLE freshmart_trade.payment_reconciliation_differences
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN payment_no VARCHAR(40) NULL COMMENT '关联支付单号',
  MODIFY COLUMN bill_entry_id BIGINT NULL COMMENT '关联账单流水',
  MODIFY COLUMN difference_type VARCHAR(32) NOT NULL COMMENT '差异类型：金额不符、单边账等',
  MODIFY COLUMN description VARCHAR(500) NOT NULL COMMENT '差异说明',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'UNHANDLED' COMMENT '处理状态：未处理、处理中、已暂存、已处理',
  MODIFY COLUMN claimed_by BIGINT NULL COMMENT '认领人',
  MODIFY COLUMN claimed_at DATETIME NULL COMMENT '认领时间',
  MODIFY COLUMN resolved_by BIGINT NULL COMMENT '处理人',
  MODIFY COLUMN resolved_at DATETIME NULL COMMENT '处理时间',
  MODIFY COLUMN resolution VARCHAR(24) NULL COMMENT '处理结论',
  MODIFY COLUMN resolution_note VARCHAR(500) NULL COMMENT '处理备注',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 支付人工核验 freshmart_trade.payment_verifications =====
ALTER TABLE freshmart_trade.payment_verifications
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN payment_no VARCHAR(40) NOT NULL COMMENT '关联支付单号',
  MODIFY COLUMN verification_type VARCHAR(32) NOT NULL COMMENT '核验方式：凭证审核、账单匹配等',
  MODIFY COLUMN proof_url VARCHAR(512) NULL COMMENT '付款凭证地址',
  MODIFY COLUMN bill_transaction_id VARCHAR(128) NULL COMMENT '匹配到的微信交易号',
  MODIFY COLUMN bill_amount DECIMAL(10,2) NULL COMMENT '账单金额，用于与支付金额比对',
  MODIFY COLUMN remark_text VARCHAR(120) NULL COMMENT '付款备注',
  MODIFY COLUMN result VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '核验结果：待核验、通过、失败',
  MODIFY COLUMN failure_message VARCHAR(300) NULL COMMENT '失败原因',
  MODIFY COLUMN verified_by BIGINT NULL COMMENT '核验人',
  MODIFY COLUMN verified_at DATETIME NULL COMMENT '核验时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

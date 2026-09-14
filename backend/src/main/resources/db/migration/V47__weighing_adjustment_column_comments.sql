-- 补齐称重调整表（freshmart_trade.weighing_adjustments）的字段中文注释。
-- 仍以 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列类型、可空性、默认值与索引。

ALTER TABLE freshmart_trade.weighing_adjustments
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单，每个子订单只允许一条称重调整',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '履约商家',
  MODIFY COLUMN prepaid_goods_amount DECIMAL(10,2) NOT NULL COMMENT '下单预估商品金额',
  MODIFY COLUMN actual_goods_amount DECIMAL(10,2) NOT NULL COMMENT '称重后的实际商品金额；逐项称重时由各项汇总',
  MODIFY COLUMN prepaid_grams INT NULL COMMENT '下单预估克数，逐项称重时由各项预占汇总',
  MODIFY COLUMN actual_grams INT NULL COMMENT '实际称重净重克数',
  MODIFY COLUMN inventory_adjust_grams INT NOT NULL DEFAULT 0 COMMENT '回补到批次的克数差额：正数补扣、负数退回',
  MODIFY COLUMN inventory_adjusted_at DATETIME NULL COMMENT '批次库存回补完成时间',
  MODIFY COLUMN difference_amount DECIMAL(10,2) NOT NULL COMMENT '实际与预估的金额差额',
  MODIFY COLUMN action VARCHAR(24) NOT NULL COMMENT '结算动作：退回用户差额、平台承担、转人工复核',
  MODIFY COLUMN refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '退回用户的钱包金额',
  MODIFY COLUMN absorbed_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台承担的金额',
  MODIFY COLUMN note VARCHAR(500) NULL COMMENT '备注',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键',
  MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '提交人',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN settled_at DATETIME NULL COMMENT '结算时间',
  MODIFY COLUMN settlement_reference VARCHAR(80) NULL COMMENT '结算引用，用于对账与幂等';

-- 为交易域核心表补充字段中文注释。
-- 说明：MySQL 没有单独修改列注释的语法，必须用 MODIFY COLUMN 重述完整定义，
-- 因此这里逐列复刻类型、可空性、默认值与自增/自动更新属性，只额外追加 COMMENT。
-- 该迁移不新增、删除或重命名任何列，也不改动索引与外键。

-- ===== 交易单 freshmart_trade.trade_orders =====
ALTER TABLE freshmart_trade.trade_orders
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN trade_no VARCHAR(40) NOT NULL COMMENT '交易单号，支付与对账的业务唯一标识',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '下单用户',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '交易单状态：待支付、已支付、已取消等',
  MODIFY COLUMN goods_amount DECIMAL(10,2) NOT NULL COMMENT '商品金额合计（优惠前）',
  MODIFY COLUMN freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '配送费',
  MODIFY COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额合计',
  MODIFY COLUMN redeemed_points INT NOT NULL DEFAULT 0 COMMENT '本单使用的积分数量',
  MODIFY COLUMN points_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣金额',
  MODIFY COLUMN payable_amount DECIMAL(10,2) NOT NULL COMMENT '应付金额：商品金额减优惠加配送费后再减积分抵扣',
  MODIFY COLUMN reservation_expires_at DATETIME NOT NULL COMMENT '库存预占失效时间，超时由任务释放',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，防止重复创建交易单',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 履约子订单 freshmart_trade.orders =====
ALTER TABLE freshmart_trade.orders
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_no VARCHAR(40) NOT NULL COMMENT '子订单号，按商家拆分后的履约单位',
  MODIFY COLUMN trade_id BIGINT NOT NULL COMMENT '所属交易单',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '下单用户',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '履约商家',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '发货仓库',
  MODIFY COLUMN delivery_zone_id BIGINT NOT NULL COMMENT '配送区域',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '子订单状态：待支付、待分拣、已拣货、已送达、已退款、已取消',
  MODIFY COLUMN goods_amount DECIMAL(10,2) NOT NULL COMMENT '商品金额（本商家部分，优惠前）',
  MODIFY COLUMN freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本商家分摊的配送费',
  MODIFY COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本商家分摊的优惠金额',
  MODIFY COLUMN redeemed_points INT NOT NULL DEFAULT 0 COMMENT '本商家分摊的积分数量',
  MODIFY COLUMN points_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本商家分摊的积分抵扣金额',
  MODIFY COLUMN payable_amount DECIMAL(10,2) NOT NULL COMMENT '应付金额（本商家部分）',
  MODIFY COLUMN actual_goods_amount DECIMAL(10,2) NULL COMMENT '称重后的实际商品金额',
  MODIFY COLUMN platform_absorbed_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台承担的称重差额',
  MODIFY COLUMN address_snapshot JSON NOT NULL COMMENT '收货地址快照，下单时固化',
  MODIFY COLUMN pricing_snapshot JSON NOT NULL COMMENT '计价快照：各优惠的叠加顺序与金额',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 订单项 freshmart_trade.order_items =====
ALTER TABLE freshmart_trade.order_items
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '所属子订单',
  MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '商品',
  MODIFY COLUMN product_name_snapshot VARCHAR(160) NOT NULL COMMENT '商品名称快照',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '发货仓库',
  MODIFY COLUMN weight_grams INT NOT NULL COMMENT '下单克数（预估计费重量）',
  MODIFY COLUMN actual_weight_grams INT NULL COMMENT '实际称重净重，逐项称重后写入',
  MODIFY COLUMN market_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '市场价（元/千克）快照',
  MODIFY COLUMN merchant_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '商家上架价（元/千克）快照',
  MODIFY COLUMN user_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '用户实付单价（元/千克），含优惠后的口径',
  MODIFY COLUMN merchant_gross_amount DECIMAL(10,2) NOT NULL COMMENT '商家商品毛额，结算与佣金基数起点',
  MODIFY COLUMN user_goods_amount DECIMAL(10,2) NOT NULL COMMENT '用户商品金额（优惠前）',
  MODIFY COLUMN actual_goods_amount DECIMAL(10,2) NULL COMMENT '实际称重后的商品金额',
  MODIFY COLUMN weighed_at DATETIME NULL COMMENT '称重完成时间',
  MODIFY COLUMN platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台价格补贴：市场价与商家上架价的差额中由平台承担的部分',
  MODIFY COLUMN batch_promotion_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '临期批次折扣金额',
  MODIFY COLUMN flash_sale_id BIGINT NULL COMMENT '参与的秒杀活动',
  MODIFY COLUMN flash_sale_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '秒杀优惠金额',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 支付单 freshmart_trade.payment_orders =====
ALTER TABLE freshmart_trade.payment_orders
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN payment_no VARCHAR(40) NOT NULL COMMENT '支付单号',
  MODIFY COLUMN trade_id BIGINT NOT NULL COMMENT '所属交易单',
  MODIFY COLUMN provider VARCHAR(24) NOT NULL DEFAULT 'PERSONAL_WECHAT_QR' COMMENT '支付方式：余额或微信',
  MODIFY COLUMN payment_mode VARCHAR(24) NOT NULL DEFAULT 'MANUAL_CONFIRMATION' COMMENT '支付模式，首期为人工确认',
  MODIFY COLUMN provider_transaction_id VARCHAR(64) NULL COMMENT '第三方交易号',
  MODIFY COLUMN code_url VARCHAR(512) NULL COMMENT '收款码地址',
  MODIFY COLUMN amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态：待支付、已支付、已退款、已取消',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，防止重复发起支付',
  MODIFY COLUMN paid_at DATETIME NULL COMMENT '支付成功时间',
  MODIFY COLUMN remark_text VARCHAR(120) NULL COMMENT '用户填写的付款备注，用于人工对账匹配',
  MODIFY COLUMN payment_proof_url VARCHAR(512) NULL COMMENT '付款凭证图片地址',
  MODIFY COLUMN verified_by BIGINT NULL COMMENT '人工核验人',
  MODIFY COLUMN verified_at DATETIME NULL COMMENT '人工核验时间',
  MODIFY COLUMN failure_code VARCHAR(48) NULL COMMENT '失败编码',
  MODIFY COLUMN failure_message VARCHAR(300) NULL COMMENT '失败原因',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 售后退款单 freshmart_trade.refund_orders =====
ALTER TABLE freshmart_trade.refund_orders
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN refund_no VARCHAR(40) NOT NULL COMMENT '退款单号',
  MODIFY COLUMN payment_id BIGINT NOT NULL COMMENT '关联支付单',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单',
  MODIFY COLUMN reason VARCHAR(300) NOT NULL COMMENT '申请原因与审核意见（审核意见追加在换行之后）',
  MODIFY COLUMN issue_type VARCHAR(32) NOT NULL COMMENT '问题类型：缺货或品质问题',
  MODIFY COLUMN evidence_description VARCHAR(1000) NOT NULL COMMENT '问题描述',
  MODIFY COLUMN evidence_images_json JSON NOT NULL COMMENT '举证图片地址数组',
  MODIFY COLUMN amount DECIMAL(10,2) NOT NULL COMMENT '退款金额',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '退款状态：待审核、待人工退款、退款成功、已驳回、退款失败',
  MODIFY COLUMN manual_refund_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '人工退款工单状态：无需人工、待处理、已完成、已失败',
  MODIFY COLUMN manual_refund_completed_at DATETIME NULL COMMENT '人工退款完成时间',
  MODIFY COLUMN manual_refund_operator_id BIGINT NULL COMMENT '人工退款操作人',
  MODIFY COLUMN manual_refund_failure_reason VARCHAR(300) NULL COMMENT '人工退款失败原因',
  MODIFY COLUMN review_mode VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_SERVICE_AI' COMMENT '审核模式：客服与 AI 辅助',
  MODIFY COLUMN reviewed_by BIGINT NULL COMMENT '审核人',
  MODIFY COLUMN reviewed_at DATETIME NULL COMMENT '审核时间',
  MODIFY COLUMN refunded_at DATETIME NULL COMMENT '退款到账时间',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，防止重复申请退款',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 售后库存处置单 freshmart_trade.refund_inventory_dispositions =====
ALTER TABLE freshmart_trade.refund_inventory_dispositions
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN refund_id BIGINT NOT NULL COMMENT '关联退款单',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单',
  MODIFY COLUMN disposition VARCHAR(32) NOT NULL COMMENT '处置结果：待检验、已回库、已报损',
  MODIFY COLUMN reason VARCHAR(120) NOT NULL COMMENT '产生处置的原因',
  MODIFY COLUMN processed_by BIGINT NULL COMMENT '处置人',
  MODIFY COLUMN processed_at DATETIME NULL COMMENT '处置时间',
  MODIFY COLUMN processing_note VARCHAR(500) NULL COMMENT '处置备注',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 订单项逐项称重明细 freshmart_trade.order_item_weighings =====
ALTER TABLE freshmart_trade.order_item_weighings
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '所属子订单',
  MODIFY COLUMN order_item_id BIGINT NOT NULL COMMENT '所属订单项，每个订单项只允许一条',
  MODIFY COLUMN adjustment_id VARCHAR(96) NOT NULL COMMENT '所属称重调整标识',
  MODIFY COLUMN prepaid_grams INT NOT NULL COMMENT '下单预估克数',
  MODIFY COLUMN actual_grams INT NOT NULL COMMENT '实际称重净重克数',
  MODIFY COLUMN prepaid_goods_amount DECIMAL(10,2) NOT NULL COMMENT '预估商品金额',
  MODIFY COLUMN actual_goods_amount DECIMAL(10,2) NOT NULL COMMENT '实际商品金额',
  MODIFY COLUMN inventory_adjust_grams INT NOT NULL DEFAULT 0 COMMENT '回补到批次的克数差额：正数补扣、负数退回',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 商家结算单 freshmart_trade.merchant_settlements =====
ALTER TABLE freshmart_trade.merchant_settlements
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '结算商家',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单',
  MODIFY COLUMN gross_amount DECIMAL(10,2) NOT NULL COMMENT '商家商品毛额（扣除商家承担优惠后）',
  MODIFY COLUMN commission_base_amount DECIMAL(10,2) NOT NULL COMMENT '佣金基数：毛额再扣除平台价格补贴',
  MODIFY COLUMN platform_price_subsidy_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台价格补贴金额，不计佣',
  MODIFY COLUMN commission_rate DECIMAL(5,2) NOT NULL COMMENT '佣金比例（百分比）',
  MODIFY COLUMN commission_amount DECIMAL(10,2) NOT NULL COMMENT '平台佣金金额',
  MODIFY COLUMN net_amount DECIMAL(10,2) NOT NULL COMMENT '商家净结算金额：毛额减佣金',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '结算状态：待结算、已结算、已反冲',
  MODIFY COLUMN settled_at DATETIME NULL COMMENT '结算时间',
  MODIFY COLUMN reversed_at DATETIME NULL COMMENT '反冲时间',
  MODIFY COLUMN reversal_reason VARCHAR(80) NULL COMMENT '反冲原因',
  MODIFY COLUMN settled_by BIGINT NULL COMMENT '确认结算的操作人',
  MODIFY COLUMN settlement_note VARCHAR(500) NULL COMMENT '结算备注',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 费用台账 freshmart_trade.fee_ledgers =====
ALTER TABLE freshmart_trade.fee_ledgers
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN trade_id BIGINT NULL COMMENT '关联交易单',
  MODIFY COLUMN order_id BIGINT NULL COMMENT '关联子订单',
  MODIFY COLUMN merchant_id BIGINT NULL COMMENT '关联商家',
  MODIFY COLUMN fee_type VARCHAR(32) NOT NULL COMMENT '费用类型：平台补贴、称重承担、退款、佣金及反冲等',
  MODIFY COLUMN amount DECIMAL(12,2) NOT NULL COMMENT '费用金额',
  MODIFY COLUMN currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  MODIFY COLUMN direction VARCHAR(8) NOT NULL COMMENT '借贷方向',
  MODIFY COLUMN reference_type VARCHAR(32) NULL COMMENT '来源单据类型',
  MODIFY COLUMN reference_id VARCHAR(80) NULL COMMENT '来源单据编号',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，同一费用不重复记账',
  MODIFY COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '费用发生时间';

-- ===== 库存预占 freshmart_trade.inventory_reservations =====
ALTER TABLE freshmart_trade.inventory_reservations
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN trade_id BIGINT NOT NULL COMMENT '关联交易单',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单',
  MODIFY COLUMN order_item_id BIGINT NOT NULL COMMENT '关联订单项',
  MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '商品',
  MODIFY COLUMN batch_id BIGINT NOT NULL COMMENT '预占的库存批次',
  MODIFY COLUMN batch_promotion_id BIGINT NULL COMMENT '命中的临期批次促销',
  MODIFY COLUMN markdown_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '促销折扣率快照',
  MODIFY COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '批次折扣金额',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '仓库',
  MODIFY COLUMN reserved_grams INT NOT NULL COMMENT '预占克数',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '预占状态：生效中、已释放',
  MODIFY COLUMN expires_at DATETIME NOT NULL COMMENT '预占失效时间',
  MODIFY COLUMN released_at DATETIME NULL COMMENT '释放时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

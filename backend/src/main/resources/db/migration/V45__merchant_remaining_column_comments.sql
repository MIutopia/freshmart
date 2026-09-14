-- 补齐商家域剩余表的字段中文注释（库存、入驻、仓配规则、促销、会员与开放 API）。
-- 仍以 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列类型、可空性、默认值与索引。

-- ===== 库存批次 freshmart_merchant.inventory_batches =====
ALTER TABLE freshmart_merchant.inventory_batches
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '商品',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '所属仓库',
  MODIFY COLUMN batch_no VARCHAR(64) NOT NULL COMMENT '批次号，同一商品在同一仓库内唯一',
  MODIFY COLUMN available_grams INT NOT NULL DEFAULT 0 COMMENT '可用克数：可被新订单预占的部分',
  MODIFY COLUMN reserved_grams INT NOT NULL DEFAULT 0 COMMENT '已预占克数：待支付订单占用',
  MODIFY COLUMN expires_on DATE NULL COMMENT '到期日期，临期进入批次促销候选',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 称收入库记录 freshmart_merchant.inventory_receipts =====
ALTER TABLE freshmart_merchant.inventory_receipts
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN receipt_no VARCHAR(48) NOT NULL COMMENT '入库单号',
  MODIFY COLUMN batch_id BIGINT NOT NULL COMMENT '入库批次',
  MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '商品',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '仓库',
  MODIFY COLUMN received_grams INT NOT NULL COMMENT '净重克数，即实际入库量',
  MODIFY COLUMN gross_grams INT NULL COMMENT '毛重克数，仅用于留痕',
  MODIFY COLUMN tare_grams INT NULL COMMENT '皮重克数，仅用于留痕',
  MODIFY COLUMN note VARCHAR(300) NULL COMMENT '备注：供应商、车次等',
  MODIFY COLUMN idempotency_key VARCHAR(96) NOT NULL COMMENT '幂等键，重复提交不会重复入库',
  MODIFY COLUMN received_by BIGINT NOT NULL COMMENT '入库操作人',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间';

-- ===== 商家入驻申请 freshmart_merchant.merchant_applications =====
ALTER TABLE freshmart_merchant.merchant_applications
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '关联商家主体',
  MODIFY COLUMN applicant_user_id BIGINT NOT NULL COMMENT '申请人',
  MODIFY COLUMN business_license_url VARCHAR(512) NULL COMMENT '营业执照图片地址',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：待审核、已通过、已驳回',
  MODIFY COLUMN review_note VARCHAR(500) NULL COMMENT '审核意见',
  MODIFY COLUMN reviewed_by BIGINT NULL COMMENT '审核人',
  MODIFY COLUMN reviewed_at DATETIME NULL COMMENT '审核时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间';

-- ===== 仓库可经营分类 freshmart_merchant.warehouse_operable_categories =====
ALTER TABLE freshmart_merchant.warehouse_operable_categories
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '仓库',
  MODIFY COLUMN category_id BIGINT NOT NULL COMMENT '允许经营的商品分类',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 商家分类仓配规则 freshmart_merchant.merchant_category_warehouse_rules =====
ALTER TABLE freshmart_merchant.merchant_category_warehouse_rules
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '商家',
  MODIFY COLUMN category_id BIGINT NOT NULL COMMENT '商品分类',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '可用仓库',
  MODIFY COLUMN priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值越小越优先命中',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 促销规则 freshmart_merchant.promotion_rules =====
ALTER TABLE freshmart_merchant.promotion_rules
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NULL COMMENT '归属商家，为空表示平台级活动',
  MODIFY COLUMN promotion_type VARCHAR(24) NOT NULL COMMENT '促销类型：满减、折扣等',
  MODIFY COLUMN name VARCHAR(120) NOT NULL COMMENT '活动名称',
  MODIFY COLUMN rule_json JSON NOT NULL COMMENT '规则内容：门槛与优惠配置',
  MODIFY COLUMN starts_at DATETIME NOT NULL COMMENT '开始时间',
  MODIFY COLUMN ends_at DATETIME NOT NULL COMMENT '结束时间',
  MODIFY COLUMN stackable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许与其他优惠叠加',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 批次促销排期 freshmart_merchant.batch_promotions =====
ALTER TABLE freshmart_merchant.batch_promotions
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN batch_id BIGINT NOT NULL COMMENT '促销批次',
  MODIFY COLUMN promotion_id BIGINT NULL COMMENT '关联的促销规则',
  MODIFY COLUMN markdown_rate DECIMAL(5,2) NOT NULL COMMENT '折扣率（百分比）',
  MODIFY COLUMN starts_at DATETIME NOT NULL COMMENT '开始时间',
  MODIFY COLUMN ends_at DATETIME NOT NULL COMMENT '结束时间，不得跨过批次过期日',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED' COMMENT '状态：已排期、进行中、已结束',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 优惠券 freshmart_merchant.coupons =====
ALTER TABLE freshmart_merchant.coupons
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NULL COMMENT '归属商家，为空表示平台券',
  MODIFY COLUMN name VARCHAR(128) NOT NULL COMMENT '优惠券名称',
  MODIFY COLUMN coupon_type VARCHAR(16) NOT NULL COMMENT '券类型：满减等',
  MODIFY COLUMN threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额',
  MODIFY COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  MODIFY COLUMN starts_at DATETIME NOT NULL COMMENT '生效时间',
  MODIFY COLUMN ends_at DATETIME NOT NULL COMMENT '失效时间',
  MODIFY COLUMN total_quantity INT NOT NULL COMMENT '发放总量',
  MODIFY COLUMN claimed_quantity INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 秒杀活动商品 freshmart_merchant.flash_sale_items =====
ALTER TABLE freshmart_merchant.flash_sale_items
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '归属商家',
  MODIFY COLUMN product_id BIGINT NOT NULL COMMENT '参与商品',
  MODIFY COLUMN sale_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '秒杀价（元/千克）',
  MODIFY COLUMN total_grams INT NOT NULL COMMENT '活动总克数',
  MODIFY COLUMN reserved_grams INT NOT NULL DEFAULT 0 COMMENT '已预占克数',
  MODIFY COLUMN sold_grams INT NOT NULL DEFAULT 0 COMMENT '已售克数',
  MODIFY COLUMN per_user_limit_grams INT NOT NULL COMMENT '单用户限购克数',
  MODIFY COLUMN starts_at DATETIME NOT NULL COMMENT '开始时间',
  MODIFY COLUMN ends_at DATETIME NOT NULL COMMENT '结束时间',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED' COMMENT '状态：已排期、进行中、已结束',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 会员等级 freshmart_merchant.membership_levels =====
ALTER TABLE freshmart_merchant.membership_levels
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN name VARCHAR(40) NOT NULL COMMENT '等级名称',
  MODIFY COLUMN min_points INT NOT NULL COMMENT '达到该等级所需成长积分',
  MODIFY COLUMN discount_rate DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT '折扣率（百分比），100 表示不打折',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态';

-- ===== 开放 API 客户端 freshmart_merchant.api_clients =====
ALTER TABLE freshmart_merchant.api_clients
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NULL COMMENT '绑定商家，为空表示平台级客户端',
  MODIFY COLUMN name VARCHAR(120) NOT NULL COMMENT '客户端名称',
  MODIFY COLUMN client_key VARCHAR(64) NOT NULL COMMENT '客户端标识',
  MODIFY COLUMN secret_hash VARCHAR(255) NOT NULL COMMENT '密钥哈希，明文仅在创建时返回一次',
  MODIFY COLUMN scopes_json JSON NOT NULL COMMENT '可访问的接口范围',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用',
  MODIFY COLUMN expires_at DATETIME NULL COMMENT '有效期，为空表示长期有效',
  MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建人',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 开放 API 访问日志 freshmart_merchant.api_access_logs =====
ALTER TABLE freshmart_merchant.api_access_logs
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN api_client_id BIGINT NOT NULL COMMENT '调用方客户端',
  MODIFY COLUMN request_id VARCHAR(64) NOT NULL COMMENT '请求标识',
  MODIFY COLUMN request_nonce VARCHAR(64) NULL COMMENT '请求随机串，用于防重放',
  MODIFY COLUMN method VARCHAR(12) NOT NULL COMMENT 'HTTP 方法',
  MODIFY COLUMN path VARCHAR(255) NOT NULL COMMENT '请求路径',
  MODIFY COLUMN response_status INT NOT NULL COMMENT '响应状态码',
  MODIFY COLUMN duration_ms INT NOT NULL COMMENT '处理耗时（毫秒）',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间';

-- 为商家域与日志域核心表补充字段中文注释。
-- 同样以 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列的类型、可空性、默认值与索引。

-- ===== 商家 freshmart_merchant.merchants =====
ALTER TABLE freshmart_merchant.merchants
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN owner_user_id BIGINT NOT NULL COMMENT '商家归属用户',
  MODIFY COLUMN name VARCHAR(128) NOT NULL COMMENT '商家名称',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '商家状态：待审核、已通过、已驳回、营业中、已停业',
  MODIFY COLUMN service_area_json JSON NULL COMMENT '服务范围描述',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 商品分类 freshmart_merchant.product_categories =====
ALTER TABLE freshmart_merchant.product_categories
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN parent_id BIGINT NULL COMMENT '父分类，为空表示一级分类',
  MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '分类名称',
  MODIFY COLUMN product_scope VARCHAR(24) NOT NULL DEFAULT 'OTHER' COMMENT '商品品类：水果、蔬菜、其他，决定适用的售后窗口',
  MODIFY COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用；停用前需先下架该分类商品';

-- ===== 商品 freshmart_merchant.products =====
ALTER TABLE freshmart_merchant.products
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '归属商家',
  MODIFY COLUMN category_id BIGINT NOT NULL COMMENT '商品分类',
  MODIFY COLUMN name VARCHAR(160) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN description TEXT NULL COMMENT '商品描述',
  MODIFY COLUMN pricing_mode VARCHAR(16) NOT NULL DEFAULT 'WEIGHT' COMMENT '计价模式：按重量计价',
  MODIFY COLUMN market_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '市场价（元/千克），上架价不得超过其上浮上限',
  MODIFY COLUMN merchant_price_per_kg DECIMAL(10,2) NOT NULL COMMENT '商家上架价（元/千克）',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT '商品状态：草稿、已上架、已下架',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 仓库 freshmart_merchant.warehouses =====
ALTER TABLE freshmart_merchant.warehouses
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '归属商家',
  MODIFY COLUMN delivery_zone_id BIGINT NOT NULL COMMENT '绑定的配送区域，一个仓库只服务一个区域',
  MODIFY COLUMN name VARCHAR(120) NOT NULL COMMENT '仓库名称',
  MODIFY COLUMN code VARCHAR(40) NOT NULL COMMENT '仓库编码',
  MODIFY COLUMN address VARCHAR(300) NOT NULL COMMENT '仓库地址',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== AI 交互审计 freshmart_log.ai_interaction_logs =====
ALTER TABLE freshmart_log.ai_interaction_logs
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN actor_user_id BIGINT NULL COMMENT '发起调用的用户',
  MODIFY COLUMN scenario VARCHAR(64) NOT NULL COMMENT '调用场景：导购、订单查询、售后审核建议等',
  MODIFY COLUMN subject_reference VARCHAR(80) NULL COMMENT '业务对象引用',
  MODIFY COLUMN model_name VARCHAR(120) NOT NULL COMMENT '调用的模型名称',
  MODIFY COLUMN request_sha256 CHAR(64) NOT NULL COMMENT '请求内容的 SHA-256 摘要，不保存原文',
  MODIFY COLUMN response_sha256 CHAR(64) NULL COMMENT '响应内容的 SHA-256 摘要，不保存原文',
  MODIFY COLUMN status VARCHAR(16) NOT NULL COMMENT '调用结果状态',
  MODIFY COLUMN error_code VARCHAR(80) NULL COMMENT '失败编码',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 审计日志 freshmart_log.audit_logs =====
ALTER TABLE freshmart_log.audit_logs
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN actor_user_id BIGINT NULL COMMENT '操作人',
  MODIFY COLUMN action_code VARCHAR(80) NOT NULL COMMENT '操作编码',
  MODIFY COLUMN resource_type VARCHAR(48) NOT NULL COMMENT '资源类型',
  MODIFY COLUMN resource_id VARCHAR(80) NULL COMMENT '资源标识',
  MODIFY COLUMN detail_json JSON NULL COMMENT '操作详情',
  MODIFY COLUMN source_ip VARCHAR(64) NULL COMMENT '来源 IP',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 节气卡片投递记录 freshmart_log.holiday_card_deliveries =====
ALTER TABLE freshmart_log.holiday_card_deliveries
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN task_id BIGINT NOT NULL COMMENT '所属卡片任务',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '接收用户，同一任务下唯一',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：待投递、投递中、已送达、投递失败',
  MODIFY COLUMN attempts INT NOT NULL DEFAULT 0 COMMENT '尝试次数，失败任务会在下一轮重试',
  MODIFY COLUMN inbox_message_id BIGINT NULL COMMENT '投递生成的站内消息',
  MODIFY COLUMN last_error VARCHAR(500) NULL COMMENT '最近一次失败原因',
  MODIFY COLUMN sent_at DATETIME NULL COMMENT '送达时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

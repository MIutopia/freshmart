-- 为用户域与配送域核心表补充字段中文注释。
-- 同样使用 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列的类型、可空性、默认值与索引。

-- ===== 用户 freshmart_user.users =====
ALTER TABLE freshmart_user.users
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN phone VARCHAR(32) NOT NULL COMMENT '手机号',
  MODIFY COLUMN login_name VARCHAR(64) NOT NULL COMMENT '登录名',
  MODIFY COLUMN password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt）',
  MODIFY COLUMN nickname VARCHAR(64) NOT NULL COMMENT '昵称',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：启用、停用',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 用户角色 freshmart_user.user_role_assignments =====
ALTER TABLE freshmart_user.user_role_assignments
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户',
  MODIFY COLUMN role_code VARCHAR(24) NOT NULL COMMENT '角色编码：管理员、运营、财务、商家、消费者、配送员',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间';

-- ===== 登录会话 freshmart_user.auth_sessions =====
ALTER TABLE freshmart_user.auth_sessions
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户',
  MODIFY COLUMN token_hash CHAR(64) NOT NULL COMMENT '访问令牌的 SHA-256 摘要',
  MODIFY COLUMN expires_at DATETIME NOT NULL COMMENT '会话过期时间',
  MODIFY COLUMN revoked_at DATETIME NULL COMMENT '吊销时间，非空表示已退出登录',
  MODIFY COLUMN last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间，用于判断是否为活跃用户',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 收货地址 freshmart_user.user_addresses =====
ALTER TABLE freshmart_user.user_addresses
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户',
  MODIFY COLUMN recipient_name VARCHAR(64) NOT NULL COMMENT '收货人姓名',
  MODIFY COLUMN recipient_phone VARCHAR(32) NOT NULL COMMENT '收货人电话',
  MODIFY COLUMN delivery_zone_id BIGINT NOT NULL COMMENT '所在配送区域',
  MODIFY COLUMN detail_address VARCHAR(300) NOT NULL COMMENT '详细地址',
  MODIFY COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为默认地址',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 钱包账户 freshmart_user.wallet_accounts =====
ALTER TABLE freshmart_user.wallet_accounts
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户',
  MODIFY COLUMN balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  MODIFY COLUMN frozen_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态：启用、停用',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 钱包流水 freshmart_user.wallet_transactions =====
ALTER TABLE freshmart_user.wallet_transactions
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN wallet_id BIGINT NOT NULL COMMENT '所属钱包账户',
  MODIFY COLUMN trade_id BIGINT NULL COMMENT '关联交易单',
  MODIFY COLUMN transaction_type VARCHAR(24) NOT NULL COMMENT '流水类型：余额支付、退款入账、称重退款等',
  MODIFY COLUMN amount DECIMAL(12,2) NOT NULL COMMENT '变动金额',
  MODIFY COLUMN balance_after DECIMAL(12,2) NOT NULL COMMENT '变动后余额，用于对账',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，同一笔资金变动不重复入账',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 积分账户 freshmart_user.user_point_accounts =====
ALTER TABLE freshmart_user.user_point_accounts
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户',
  MODIFY COLUMN available_points INT NOT NULL DEFAULT 0 COMMENT '可用积分余额',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 积分流水 freshmart_user.points_transactions =====
ALTER TABLE freshmart_user.points_transactions
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户',
  MODIFY COLUMN trade_id BIGINT NULL COMMENT '关联交易单',
  MODIFY COLUMN change_amount INT NOT NULL COMMENT '变动积分，正数为发放、负数为扣减或回退',
  MODIFY COLUMN balance_after INT NOT NULL COMMENT '变动后余额',
  MODIFY COLUMN reason VARCHAR(120) NOT NULL COMMENT '变动原因：支付发放、退款回退等',
  MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键，同一笔积分变动不重复入账',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 用户优惠券 freshmart_user.user_coupons =====
ALTER TABLE freshmart_user.user_coupons
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '持有用户',
  MODIFY COLUMN coupon_id BIGINT NOT NULL COMMENT '优惠券定义',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：可用、已使用、已失效',
  MODIFY COLUMN claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  MODIFY COLUMN used_trade_id BIGINT NULL COMMENT '使用的交易单',
  MODIFY COLUMN used_at DATETIME NULL COMMENT '使用时间';

-- ===== 用户会员权益 freshmart_user.user_memberships =====
ALTER TABLE freshmart_user.user_memberships
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户',
  MODIFY COLUMN level_id BIGINT NOT NULL COMMENT '会员等级',
  MODIFY COLUMN points INT NOT NULL DEFAULT 0 COMMENT '用于等级判定与折扣的成长积分',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 通知偏好 freshmart_user.user_notification_preferences =====
ALTER TABLE freshmart_user.user_notification_preferences
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户',
  MODIFY COLUMN seasonal_card_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否接收节气 / 节日卡片推送',
  MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ===== 配送区域 freshmart_delivery.delivery_zones =====
ALTER TABLE freshmart_delivery.delivery_zones
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN name VARCHAR(80) NOT NULL COMMENT '区域名称',
  MODIFY COLUMN area_code VARCHAR(32) NOT NULL COMMENT '区域编码',
  MODIFY COLUMN boundary_json JSON NULL COMMENT '区域边界描述',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：启用、停用';

-- ===== 配送任务 freshmart_delivery.delivery_tasks =====
ALTER TABLE freshmart_delivery.delivery_tasks
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN order_id BIGINT NOT NULL COMMENT '关联子订单，每个商家子订单一条任务',
  MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '履约商家',
  MODIFY COLUMN warehouse_id BIGINT NOT NULL COMMENT '取货仓库',
  MODIFY COLUMN delivery_zone_id BIGINT NOT NULL COMMENT '配送区域',
  MODIFY COLUMN rider_user_id BIGINT NULL COMMENT '接单配送员',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'WAITING_ASSIGNMENT' COMMENT '任务状态：待派单、已派单、已接单、已取货、配送中、已送达、异常',
  MODIFY COLUMN accept_deadline_at DATETIME NULL COMMENT '接单截止时间，超时由任务回收到派单池',
  MODIFY COLUMN assigned_at DATETIME NULL COMMENT '派单时间',
  MODIFY COLUMN accepted_at DATETIME NULL COMMENT '接单时间',
  MODIFY COLUMN picked_at DATETIME NULL COMMENT '取货时间',
  MODIFY COLUMN delivered_at DATETIME NULL COMMENT '送达时间，售后窗口以此为起点',
  MODIFY COLUMN timeout_at DATETIME NULL COMMENT '超时标记时间',
  MODIFY COLUMN proof_url VARCHAR(512) NULL COMMENT '送达凭证图片地址',
  MODIFY COLUMN exception_note VARCHAR(500) NULL COMMENT '异常说明';

-- ===== 骑手档案 freshmart_delivery.rider_profiles =====
ALTER TABLE freshmart_delivery.rider_profiles
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '关联用户账号',
  MODIFY COLUMN employee_no VARCHAR(40) NOT NULL COMMENT '骑手工号',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '在岗状态：在职、离职',
  MODIFY COLUMN current_zone_id BIGINT NULL COMMENT '当前负责的配送区域';

-- ===== 骑手日绩效 freshmart_delivery.rider_performance_daily =====
ALTER TABLE freshmart_delivery.rider_performance_daily
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN rider_user_id BIGINT NOT NULL COMMENT '配送员',
  MODIFY COLUMN stat_date DATE NOT NULL COMMENT '统计日期',
  MODIFY COLUMN assigned_count INT NOT NULL DEFAULT 0 COMMENT '被派单数',
  MODIFY COLUMN accepted_count INT NOT NULL DEFAULT 0 COMMENT '接单数',
  MODIFY COLUMN delivered_count INT NOT NULL DEFAULT 0 COMMENT '送达数',
  MODIFY COLUMN timeout_count INT NOT NULL DEFAULT 0 COMMENT '接单超时次数',
  MODIFY COLUMN on_time_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '准时率（百分比）';

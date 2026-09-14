-- 为所有表补充中文注释。
-- 背景：面向的最终用户与运维人员为中文使用者，且主库中混有 V27 之前的历史遗留表，
-- 缺少注释时无法从表名判断归属与用途。这里同时把遗留表显式标注出来，便于后续清理。
-- 注意：本迁移只设置 COMMENT，不改变任何列的类型、可空性、默认值与索引。

-- ===== 主库 freshmart：平台规则、迁移历史与兼容遗留表 =====
ALTER TABLE freshmart.ai_recommendation_events COMMENT='AI 推荐事件（历史遗留表，已迁至日志/推荐链路）';
ALTER TABLE freshmart.api_access_logs COMMENT='开放 API 访问日志（历史遗留表）';
ALTER TABLE freshmart.api_clients COMMENT='开放 API 客户端（历史遗留表）';
ALTER TABLE freshmart.audit_logs COMMENT='审计日志（历史遗留表，现为 freshmart_log.audit_logs）';
ALTER TABLE freshmart.auth_sessions COMMENT='登录会话（历史遗留表，现为 freshmart_user.auth_sessions）';
ALTER TABLE freshmart.batch_promotions COMMENT='批次促销排期（历史遗留表，现为 freshmart_merchant.batch_promotions）';
ALTER TABLE freshmart.coupons COMMENT='优惠券（历史遗留表，现为 freshmart_merchant.coupons）';
ALTER TABLE freshmart.delivery_tasks COMMENT='配送任务（历史遗留表，现为 freshmart_delivery.delivery_tasks）';
ALTER TABLE freshmart.delivery_zones COMMENT='配送区域（历史遗留表，现为 freshmart_delivery.delivery_zones）';
ALTER TABLE freshmart.electronic_receipts COMMENT='电子小票（历史遗留表，现为 freshmart_trade.electronic_receipts）';
ALTER TABLE freshmart.flyway_schema_history COMMENT='Flyway 迁移历史：主库所有版本化变更的执行记录';
ALTER TABLE freshmart.inbox_messages COMMENT='站内消息（历史遗留表，现为 freshmart_log.inbox_messages）';
ALTER TABLE freshmart.inventory_batches COMMENT='库存批次（历史遗留表，现为 freshmart_merchant.inventory_batches）';
ALTER TABLE freshmart.inventory_reservations COMMENT='库存预占（历史遗留表，现为 freshmart_trade.inventory_reservations）';
ALTER TABLE freshmart.media_assets COMMENT='媒体资产（历史遗留表，现为 freshmart_log.media_assets）';
ALTER TABLE freshmart.membership_levels COMMENT='会员等级（历史遗留表，现为 freshmart_merchant.membership_levels）';
ALTER TABLE freshmart.merchant_applications COMMENT='商家入驻申请（历史遗留表，现为 freshmart_merchant.merchant_applications）';
ALTER TABLE freshmart.merchant_category_warehouse_rules COMMENT='商家分类仓配规则（历史遗留表，现为 freshmart_merchant 同名表）';
ALTER TABLE freshmart.merchant_delivery_zones COMMENT='商家配送区域绑定（历史遗留表）';
ALTER TABLE freshmart.merchant_settlements COMMENT='商家结算单（历史遗留表，现为 freshmart_trade.merchant_settlements）';
ALTER TABLE freshmart.merchants COMMENT='商家（历史遗留表，现为 freshmart_merchant.merchants）';
ALTER TABLE freshmart.order_item_batch_allocations COMMENT='订单项批次分配（历史遗留表，现为 freshmart_trade 同名表）';
ALTER TABLE freshmart.order_items COMMENT='订单项（历史遗留表，现为 freshmart_trade.order_items）';
ALTER TABLE freshmart.order_status_logs COMMENT='订单状态变更日志（历史遗留表）';
ALTER TABLE freshmart.orders COMMENT='履约子订单（历史遗留表，现为 freshmart_trade.orders）';
ALTER TABLE freshmart.payment_orders COMMENT='支付单（历史遗留表，现为 freshmart_trade.payment_orders）';
ALTER TABLE freshmart.platform_commission_rules COMMENT='平台佣金规则：按商家或分类配置的佣金比例';
ALTER TABLE freshmart.platform_rules COMMENT='平台规则：配送费、预占时长、称重误差上限、积分倍率与售后窗口等运行时参数';
ALTER TABLE freshmart.points_transactions COMMENT='积分流水（历史遗留表，现为 freshmart_user.points_transactions）';
ALTER TABLE freshmart.product_categories COMMENT='商品分类（历史遗留表，现为 freshmart_merchant.product_categories）';
ALTER TABLE freshmart.products COMMENT='商品（历史遗留表，现为 freshmart_merchant.products）';
ALTER TABLE freshmart.promotion_rules COMMENT='促销规则（历史遗留表，现为 freshmart_merchant.promotion_rules）';
ALTER TABLE freshmart.refund_orders COMMENT='售后退款单（历史遗留表，现为 freshmart_trade.refund_orders）';
ALTER TABLE freshmart.rider_performance_daily COMMENT='骑手日绩效（历史遗留表，现为 freshmart_delivery.rider_performance_daily）';
ALTER TABLE freshmart.rider_profiles COMMENT='骑手档案（历史遗留表，现为 freshmart_delivery.rider_profiles）';
ALTER TABLE freshmart.trade_orders COMMENT='交易单：一次多商家结算的支付聚合单（历史遗留表，现为 freshmart_trade.trade_orders）';
ALTER TABLE freshmart.user_coupons COMMENT='用户优惠券（历史遗留表，现为 freshmart_user.user_coupons）';
ALTER TABLE freshmart.user_memberships COMMENT='用户会员权益（历史遗留表，现为 freshmart_user.user_memberships）';
ALTER TABLE freshmart.user_role_assignments COMMENT='用户角色分配（历史遗留表，现为 freshmart_user.user_role_assignments）';
ALTER TABLE freshmart.users COMMENT='用户（历史遗留表，现为 freshmart_user.users）';
ALTER TABLE freshmart.wallet_accounts COMMENT='钱包账户（历史遗留表，现为 freshmart_user.wallet_accounts）';
ALTER TABLE freshmart.wallet_transactions COMMENT='钱包流水（历史遗留表，现为 freshmart_user.wallet_transactions）';
ALTER TABLE freshmart.warehouse_operable_categories COMMENT='仓库可经营分类（历史遗留表，现为 freshmart_merchant 同名表）';
ALTER TABLE freshmart.warehouses COMMENT='仓库（历史遗留表，现为 freshmart_merchant.warehouses）';
ALTER TABLE freshmart.weighing_adjustments COMMENT='称重调整（历史遗留表，现为 freshmart_trade.weighing_adjustments）';

-- ===== 用户域 freshmart_user =====
ALTER TABLE freshmart_user.auth_sessions COMMENT='登录会话：记录令牌会话、最后活跃时间与吊销状态';
ALTER TABLE freshmart_user.points_transactions COMMENT='积分流水：消费发放与退款回退，含幂等键';
ALTER TABLE freshmart_user.user_addresses COMMENT='用户收货地址：下单时快照到订单';
ALTER TABLE freshmart_user.user_coupons COMMENT='用户持有优惠券：领取、使用与失效状态';
ALTER TABLE freshmart_user.user_memberships COMMENT='用户会员权益：等级、有效期与折扣快照';
ALTER TABLE freshmart_user.user_notification_preferences COMMENT='用户通知偏好：是否接收节气卡片等站内推送';
ALTER TABLE freshmart_user.user_point_accounts COMMENT='用户积分账户：可用积分余额';
ALTER TABLE freshmart_user.user_role_assignments COMMENT='用户角色分配：决定可访问的端与接口';
ALTER TABLE freshmart_user.users COMMENT='用户账号：登录名、密码哈希、手机号与状态';
ALTER TABLE freshmart_user.wallet_accounts COMMENT='用户钱包账户：可用余额，用于余额支付与退款回退';
ALTER TABLE freshmart_user.wallet_transactions COMMENT='钱包流水：支付扣款、退款入账与人工调整，含幂等键';

-- ===== 商家域 freshmart_merchant =====
ALTER TABLE freshmart_merchant.api_access_logs COMMENT='开放 API 访问日志：记录客户端、路径与响应结果';
ALTER TABLE freshmart_merchant.api_clients COMMENT='开放 API 客户端：密钥哈希、scope 与有效期';
ALTER TABLE freshmart_merchant.batch_promotions COMMENT='批次促销排期：指定批次的低价促销时间段';
ALTER TABLE freshmart_merchant.coupons COMMENT='优惠券定义：门槛、面额、发放总量与有效期';
ALTER TABLE freshmart_merchant.flash_sale_items COMMENT='秒杀活动商品：秒杀价、总克数与单用户限购克数';
ALTER TABLE freshmart_merchant.inventory_batches COMMENT='库存批次：商品在某仓库的可用与预占克数、过期日期';
ALTER TABLE freshmart_merchant.inventory_receipts COMMENT='称收入库记录：净重入库、毛重与皮重留痕，含幂等键';
ALTER TABLE freshmart_merchant.membership_levels COMMENT='会员等级定义：等级阈值与折扣比例';
ALTER TABLE freshmart_merchant.merchant_applications COMMENT='商家入驻申请：营业执照、审核状态与申请人';
ALTER TABLE freshmart_merchant.merchant_category_warehouse_rules COMMENT='商家分类仓配规则：商家+分类+仓库的有效组合与优先级';
ALTER TABLE freshmart_merchant.merchants COMMENT='商家主体：归属人、名称与营业状态';
ALTER TABLE freshmart_merchant.product_categories COMMENT='商品分类：品类决定适用的售后窗口，停用前需先下架商品';
ALTER TABLE freshmart_merchant.products COMMENT='商品：归属商家与分类，保存市场价与商家上架价快照源';
ALTER TABLE freshmart_merchant.promotion_rules COMMENT='促销规则：满减、折扣等可叠加活动的规则快照';
ALTER TABLE freshmart_merchant.warehouse_operable_categories COMMENT='仓库可经营分类：仓库允许经营的商品分类白名单';
ALTER TABLE freshmart_merchant.warehouses COMMENT='仓库：归属商家、绑定配送区域与地址';

-- ===== 配送域 freshmart_delivery =====
ALTER TABLE freshmart_delivery.delivery_tasks COMMENT='配送任务：每个商家子订单一条，含接单超时与送达凭证';
ALTER TABLE freshmart_delivery.delivery_zones COMMENT='配送区域：下单时校验用户地址是否在服务范围内';
ALTER TABLE freshmart_delivery.rider_performance_daily COMMENT='骑手日绩效：按天统计接单量、准时率等指标';
ALTER TABLE freshmart_delivery.rider_profiles COMMENT='骑手档案：关联用户账号与在岗状态';

-- ===== 交易域 freshmart_trade =====
ALTER TABLE freshmart_trade.electronic_receipts COMMENT='电子小票：订单结算明细快照';
ALTER TABLE freshmart_trade.fee_ledgers COMMENT='费用台账：平台补贴、称重承担、退款与佣金的资金事实来源';
ALTER TABLE freshmart_trade.financial_status_logs COMMENT='资金状态日志：支付、退款与库存处置的状态流转留痕';
ALTER TABLE freshmart_trade.flash_sale_reservations COMMENT='秒杀库存预占：活动库存的占用与释放';
ALTER TABLE freshmart_trade.inventory_reservations COMMENT='库存预占：未支付订单的克数占用与超时释放';
ALTER TABLE freshmart_trade.merchant_settlements COMMENT='商家结算单：佣金基数、佣金、净结算金额与确认信息';
ALTER TABLE freshmart_trade.order_item_batch_allocations COMMENT='订单项批次分配：订单项实际占用各批次的克数与折扣快照';
ALTER TABLE freshmart_trade.order_item_weighings COMMENT='订单项逐项称重明细：项级预估与实际克数、金额及库存回补克数';
ALTER TABLE freshmart_trade.order_items COMMENT='订单项：商品、数量、价格快照与实际称重结果';
ALTER TABLE freshmart_trade.orders COMMENT='履约子订单：按商家拆分，含应付、实际金额与平台承担金额';
ALTER TABLE freshmart_trade.payment_bill_entries COMMENT='微信账单流水：导入的收款明细，用于与支付单匹配';
ALTER TABLE freshmart_trade.payment_bill_imports COMMENT='微信账单导入批次：导入时间、来源文件与处理结果';
ALTER TABLE freshmart_trade.payment_orders COMMENT='支付单：交易单的支付记录，含方式、状态与幂等键';
ALTER TABLE freshmart_trade.payment_reconciliation_differences COMMENT='支付对账差异：账单与支付单不匹配的记录及处理状态';
ALTER TABLE freshmart_trade.payment_verifications COMMENT='支付人工核验记录：付款凭证、核验人与结论';
ALTER TABLE freshmart_trade.refund_inventory_dispositions COMMENT='售后库存处置单：退款后退货回库或报损的处理留痕';
ALTER TABLE freshmart_trade.refund_orders COMMENT='售后退款单：问题类型、举证、审核与退款执行状态';
ALTER TABLE freshmart_trade.trade_orders COMMENT='交易单：一次多商家结算的支付聚合单，含应付与积分抵扣';
ALTER TABLE freshmart_trade.weighing_adjustments COMMENT='称重调整：整单或逐项称重结果、金额差额与批次库存回补克数';

-- ===== 日志域 freshmart_log =====
ALTER TABLE freshmart_log.ai_interaction_logs COMMENT='AI 交互摘要审计：仅保存请求与响应的 SHA-256 摘要及元数据';
ALTER TABLE freshmart_log.audit_logs COMMENT='审计日志：管理端与商家端的关键操作留痕';
ALTER TABLE freshmart_log.holiday_card_deliveries COMMENT='节气卡片投递记录：按用户记录投递状态与失败原因';
ALTER TABLE freshmart_log.holiday_card_tasks COMMENT='节气卡片任务：节气键、祝福语、计划时间与执行结果';
ALTER TABLE freshmart_log.inbox_messages COMMENT='站内消息：支付、退款与节气卡片通知，含 SVG 卡片内容与幂等键';
ALTER TABLE freshmart_log.integration_outbox COMMENT='集成出站事件：待可靠投递的领域事件';
ALTER TABLE freshmart_log.media_assets COMMENT='媒体资产：上传文件的内容类型、大小与访问控制归属';

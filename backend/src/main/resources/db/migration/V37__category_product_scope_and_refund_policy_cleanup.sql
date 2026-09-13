-- 售后窗口差异化：水果与蔬菜的保鲜期差异很大，需要按商品品类匹配不同的申请窗口。
-- 分类是商品唯一的品类来源，因此在这里补充品类标记；窗口时长本身仍由 platform_rules 统一维护
-- （refund.fruit.window.minutes / refund.vegetable.window.minutes / refund.default.window.minutes）。
ALTER TABLE freshmart_merchant.product_categories
  ADD COLUMN product_scope VARCHAR(24) NOT NULL DEFAULT 'OTHER' AFTER name;

-- refund_policies 自 V3 建立后从未被任何代码引用，其 window_minutes 全为 1440 默认值，
-- 与 platform_rules 中实际生效的 60/4320 相互冲突，属于并存的第二个配置源。
-- 售后窗口的权威来源统一为 platform_rules，故移除该表以免后续误用。
DROP TABLE IF EXISTS freshmart.refund_policies;

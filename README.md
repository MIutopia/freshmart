# FreshMart 生鲜配送商城

面向生鲜果蔬的多商家配送商城，按四类业务端组织：**用户端**、**商家端**、**配送端**、**平台管理后台**。

技术栈为 Java 17 + Spring Boot 3.3.5 + MySQL 8 + Vue 3 + Vite + Element Plus。系统采用**多库分域**架构：业务数据按领域拆分为 5 个卫星库，主库仅保留平台规则与迁移历史。

系统的核心特征是**按重量计价**：下单只能给出预估克数，实际交付重量在分拣称重后确定，因此必须在**金额**与**库存克数**两个维度上同时保证账实一致。

---

## 一、功能范围

### 已实现

| 领域 | 能力 |
| --- | --- |
| 认证与权限 | 账号密码登录、令牌会话、六种角色（管理员/运营/财务/商家/消费者/配送员）、接口级授权 |
| 商家入驻 | 提交申请、营业执照、审核通过或驳回、商家与仓库建立 |
| 商品与分类 | 商品上架下架、分类管理、**品类标记（水果/蔬菜/其他）决定售后窗口**、价格规则校验 |
| 仓储与库存 | 仓库、批次、**称收入库（净重/毛重/皮重）**、分类仓配规则、仓库可经营分类白名单 |
| 交易 | 交易单与子订单、库存预占与超时释放、优惠与积分抵扣、电子小票、批次追溯 |
| 支付 | 余额支付、个人微信收款码（人工核验）、付款凭证、**账单导入与对账差异处理** |
| 称重结算 | **整单与逐项两种粒度的称重调整**、平台误差上限承担、克数差额**按批次回补** |
| 售后 | 缺货与品质问题申请、举证图片、**按品类区分的售后窗口**、AI 辅助审核建议、退款执行、**退货回库按实际出库量结算** |
| 配送 | 配送区域、派单与接单、接单超时回收、送达凭证、骑手日绩效 |
| 结算 | 佣金计算（基数排除平台价格补贴）、结算确认、退款反冲 |
| 营销 | 优惠券、会员等级与折扣、满减规则、**临期批次促销（不得跨过期日）**、秒杀与限购 |
| 通知 | 站内消息、节气与节日 SVG 卡片、按节气自动触发、**用户偏好开关与卡片预览** |
| AI | DeepSeek 导购与本人订单查询、交互摘要审计（只存 SHA-256 摘要） |
| 开放 API | 客户端密钥、HMAC 签名、scope 授权、访问日志、五类只读查询 |
| 运营 | 经营看板（销售额、客单价、销量排行、库存预警、履约指标）、平台规则后台化管理 |

### 未实现（明确的边界）

- 真实微信商户号对接：当前为个人收款码 + 人工核验，支付适配器为可替换边界
- 对象存储：媒体文件落本地磁盘（`./data/media`）
- Redis、分布式锁、消息队列：当前为单实例部署
- 监控与限流：仅暴露 `health` 与 `info` 端点
- 生产拆库后的 Outbox 补偿机制：`integration_outbox` 表已建，投递器未实现

---

## 二、技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.5、Spring Security、JdbcTemplate、Flyway |
| 数据库 | MySQL 8（1 个主库 + 5 个卫星库，共 102 张表） |
| 前端 | Vue 3、TypeScript、Vite 5、Element Plus、Pinia |
| 测试 | JUnit 5（单元）、PowerShell 端到端脚本 |
| 外部服务 | DeepSeek API（可选，未配置时 AI 功能不可用） |

---

## 三、目录结构

```text
freshmart/
├── backend/
│   ├── config/                本地配置（application-local.yml，不入 Git）
│   ├── data/                  媒体文件落盘目录
│   ├── scripts/
│   │   ├── bootstrap-database.sql          主库初始化
│   │   ├── bootstrap-domain-databases.sql  5 个卫星库初始化
│   │   └── e2e-smoke.ps1                   端到端测试脚本
│   ├── src/main/java/com/freshmart/        91 个 Java 文件
│   ├── src/main/resources/db/migration/    47 个 Flyway 迁移
│   └── src/test/java/                      16 个测试类
├── frontend/
│   └── src/
│       ├── api/               接口封装
│       ├── constants/         业务字典（19 组中英映射）
│       ├── router/            路由与导航
│       ├── stores/            状态管理
│       └── views/             34 个页面，按 app / merchant / delivery / admin 分目录
└── docs/                      23 份设计与规则文档
```

---

## 四、环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 后端编译与运行 |
| Maven | 3.9+ | 构建工具 |
| MySQL | 8.0 | 需支持 `utf8mb4` 与窗口函数 |
| Node.js | 20+ | 前端构建 |
| PowerShell | 5.1 或 7 | 运行端到端脚本（两者均已验证） |

---

## 五、快速开始

### 1. 初始化数据库

先建主库（含 Flyway 历史与平台规则），再建 5 个卫星库：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < backend/scripts/bootstrap-database.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < backend/scripts/bootstrap-domain-databases.sql
```

> 两个脚本均可**重复执行**（建表语句为 `IF NOT EXISTS`），也可先在沙箱库演练后再落到正式库。

剩余的表结构与种子数据由 **Flyway 在应用启动时自动应用**（共 47 个迁移），无需手工执行。

### 2. 配置

数据库连接默认指向 `127.0.0.1:3307`，可用环境变量覆盖：

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/freshmart
DB_USERNAME=freshmart
DB_PASSWORD=<your-password>
```

5 个卫星库各有独立变量：`USER_DB_URL`、`MERCHANT_DB_URL`、`DELIVERY_DB_URL`、`TRADE_DB_URL`、`LOG_DB_URL`，未设置时回落到主库配置。

如需 AI 功能，创建 `backend/config/application-local.yml`：

```yaml
ai:
  deepseek:
    api-key: <your-key>
```

**该文件与所有本地凭据均不得提交到 Git。**

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务监听 `8080`。启动时会执行两件事：

1. Flyway 应用未执行的迁移；
2. `DomainSchemaReadinessVerifier` 校验 5 个卫星库的表与关键列 —— **缺失即拒绝启动**，避免结构不一致导致运行期才暴露故障。

健康检查：

```bash
curl http://127.0.0.1:8080/actuator/health
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite 已配置代理，将 `/api`、`/open-api`、`/public` 转发到 `127.0.0.1:8080`。

四端入口：

| 端 | 路径 |
| --- | --- |
| 用户端 | `/app` |
| 商家端 | `/merchant` |
| 配送端 | `/delivery` |
| 管理后台 | `/admin` |

---

## 六、测试账号

由迁移 `V28__seed_satellite_test_accounts.sql` 预置，**仅供本地开发联调**：

| 身份 | 账号 | 数量 | 密码 |
| --- | --- | ---: | --- |
| 管理员 | `admin-test-01` | 1 | `password` |
| 用户 | `consumer-test-01` ~ `consumer-test-10` | 10 | `password` |
| 商家 | `merchant-test-01` ~ `merchant-test-10` | 10 | `password` |
| 配送员 | `rider-test-01` ~ `rider-test-10` | 10 | `password` |

部署到共享环境前必须移除这些账号。

---

## 七、测试与验证

### 单元测试

16 个测试类覆盖价格、运费、积分、促销、结算、称重与仓储边界等**纯函数策略**：

```bash
cd backend
mvn test
```

### 端到端测试

`backend/scripts/e2e-smoke.ps1` 覆盖四端主流程共 **96 项断言**（含反向断言，如「有在架商品时停用分类必须被拒绝」）：

```bash
# Windows PowerShell 5.1
powershell -ExecutionPolicy Bypass -File backend/scripts/e2e-smoke.ps1

# PowerShell 7
pwsh -ExecutionPolicy Bypass -File backend/scripts/e2e-smoke.ps1
```

运行前需保证后端已在 `8080` 运行。脚本覆盖：登录鉴权、平台配置、商家商品与批次、**称收入库**、下单支付、**整单与逐项称重**、售后与**退货回库**、配送派单、结算反冲、库存处置、节气卡片与预览。

### 数据库结构核对

迁移前可先导出列结构快照，执行后再比对，确认只发生预期变更：

```sql
SELECT CONCAT(TABLE_SCHEMA, '.', TABLE_NAME, '.', COLUMN_NAME, '|', COLUMN_TYPE, '|',
              IS_NULLABLE, '|', IFNULL(COLUMN_DEFAULT, 'NULL'), '|', EXTRA)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA LIKE 'freshmart%'
ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;
```

---

## 八、核心业务规则

理解以下四条规则，才能正确理解系统的数据模型：

1. **按重量计价与预占**：下单时按预估克数预占批次库存，未支付超时（默认 15 分钟）自动释放。
2. **称重结算**：实际净重与预估的**金额差额**在平台上限（默认 1.50 元）内由平台承担，超出转人工复核；**克数差额**按订单项各自批次预占比例回补，最后一批兜底剩余以避免取整误差。
3. **售后窗口按品类区分**：水果与蔬菜保鲜期差异大，窗口取订单商品品类中**最长**的一条，避免混合订单被较短窗口提前卡住。
4. **退款回库按实际出库量**：退货回补的克数等于该订单项的**实际称重净重**（未称重时回落预占克数），而非下单预占量。

---

## 九、文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/系统架构设计.md` | 分层与模块划分 |
| `docs/多库数据架构.md` | 六个库的职责边界与校验机制 |
| `docs/数据库清单.md` | 102 张表的清单与字段说明 |
| `docs/数据库初始化.md` | 建库步骤与迁移说明 |
| `docs/业务规则与状态机.md` | 各业务对象的状态流转 |
| `docs/交易与模拟支付.md` | 下单与支付链路 |
| `docs/价格与仓配结算规则.md` | 计价、佣金与结算口径 |
| `docs/商品与仓储库存.md` | 商品、批次与库存模型 |
| `docs/认证与商家入驻.md` | 鉴权与入驻流程 |
| `docs/配送区域与骑手工作台.md` | 配送域设计 |
| `docs/营销优惠与积分.md` | 优惠叠加与积分规则 |
| `docs/AI导购与节日卡片.md` | AI 边界与卡片推送 |
| `docs/接口草案.md` | 接口约定 |
| `docs/本地测试账号.md` | 测试账号说明 |
| `docs/项目过程总览.md` | 进度与待办 |
| `docs/开发变更日志.md` | 逐条变更记录 |
| `docs/实验设计方案.md` | 一致性验证的实验设计 |
| `docs/项目完成度评估报告.docx` | 完成度评估 |

---

## 十、开发约定

- **幂等**：所有写操作携带 `Idempotency-Key`；交易、支付、退款、称重、入库均保证重复提交只生效一次。
- **审计**：管理端与商家端的关键操作写入 `audit_logs`，资金状态变更写入 `financial_status_logs`。
- **状态前置校验**：非法状态转换返回 409 而非静默忽略，例如对未送达订单申请退款、对已处置的退货单重复处置。
- **数据库注释**：新增表与字段必须写中文注释；修改既有列注释需用 `MODIFY COLUMN` 复刻完整定义，并比对执行前后的结构快照。
- **界面文案**：页面不得直接渲染后端英文枚举，统一经 `frontend/src/constants/dictionaries.ts` 转中文。
- **提交粒度**：每个可交付阶段一个 commit，完整验证后再合并 `main`。

---

## 十一、已知限制

- 单实例部署，未验证多实例下的并发行为
- 端到端测试为顺序执行，不覆盖并发场景
- 服务层与持久层缺少单元测试，中间层依赖端到端测试覆盖
- 媒体文件存本地磁盘，多实例部署前需替换为对象存储

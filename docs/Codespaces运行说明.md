# 在 GitHub Codespaces 中运行 FreshMart

> 适用场景：不安装任何本机环境，用浏览器打开完整可用的系统（MySQL + 后端 + 前端）
> 相关配置：`.devcontainer/`、`frontend/vite.config.mjs`

---

## 一、这套方案解决什么问题

FreshMart 是前后端分离的 Java + Vue 应用，常规运行需要本机同时具备 JDK 17、Maven、MySQL 8 与 Node 20。Codespaces 提供一台云端 Ubuntu 容器，把上述环境一次性装好，你只需要一个浏览器即可访问完整系统。

**适合**：换电脑演示、答辩或汇报时现场打开、协作时让别人快速复现、不想在本机装 MySQL。

**不适合**：需要长期对外提供服务的正式上线（应部署到云服务器）。

---

## 二、为什么不用 GitHub Pages

GitHub Pages 只能托管静态文件，**无法运行 Spring Boot 后端**。若把前端单独部署到 Pages，会同时撞上四个障碍：

| 障碍 | 具体表现 |
| --- | --- |
| 没有后端 | 所有接口 404，登录页之后任何操作都失败 |
| CORS 白名单 | 后端只允许 `http://localhost:5173`，Pages 域名会被浏览器拒绝 |
| history 路由 | 前端用 `createWebHistory()`，子路径刷新会返回 GitHub 的 404 页 |
| API 基址 | 默认是相对路径 `/api`，在 Pages 上会解析到错误地址 |

Codespaces 把前后端放进同一个容器，从根本上避开了这些问题——**后端代码与配置无需任何修改**。

---

## 三、前置条件

- 一个 GitHub 账号（免费账号即可，含每月约 60–120 核心小时的 Codespaces 额度）
- 浏览器
- 无需本机安装任何软件

---

## 四、使用步骤

### 步骤 1：创建 Codespace

1. 打开仓库页面 `https://github.com/MIutopia/freshmart`
2. 点击绿色的 `Code` 按钮
3. 切到 `Codespaces` 标签页
4. 点击 `Create codespace on main`

### 步骤 2：等待自动初始化（约 3–5 分钟）

容器创建后会自动执行 `.devcontainer/setup.sh`，完成以下工作：

```text
1/5  安装 MySQL 8
2/5  启动 MySQL 并将 root 密码设为 freshmart-dev
3/5  创建六个数据库（freshmart + 五个卫星库）
4/5  把六个数据源地址写入 ~/.bashrc
5/5  安装前端 npm 依赖与后端 Maven 依赖
```

终端出现「初始化完成」即表示就绪。

### 步骤 3：启动系统

在终端执行：

```bash
bash .devcontainer/dev.sh
```

脚本会依次完成：

```text
1. 检查 MySQL 是否在运行，未运行则启动
2. 检查 8080 与 5173 端口是否被占用
3. 后台启动后端（日志 work/boot-8080.log）
4. 后台启动前端（日志 work/vite-5173.log）
5. 轮询健康端点，等待后端完成 Flyway 迁移与结构校验（最长 180 秒）
```

看到下面这样的输出即表示成功：

```text
后端就绪：http://127.0.0.1:8080/actuator/health
前端页面：请打开「端口」面板中的 5173 公网地址
测试账号：admin-test-01 / merchant-test-01 / consumer-test-01 / rider-test-01，密码均为 password
```

> 首次启动较慢（需执行 47 个 Flyway 迁移），后续启动通常在 30 秒内完成。

### 步骤 4：打开前端页面

1. 在 VS Code 底部面板点击 **「端口 / Ports」** 标签
2. 找到标为 **「前端页面（打开这个）」** 的 **5173** 端口
3. 点击该行右侧的 **地球图标**，浏览器会打开新标签页

> 也可以按 `F1` 输入 `Forward a Port` 手动转发。
> 若提示需要授权，选择 **Public** 才能用手机等其他设备访问。

### 步骤 5：登录

| 身份 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin-test-01` | `password` |
| 商家 | `merchant-test-01` | `password` |
| 用户 | `consumer-test-01` | `password` |
| 配送员 | `rider-test-01` | `password` |

登录后按角色自动跳转到对应控制台：

| 端 | 路径 |
| --- | --- |
| 用户端 | `/app` |
| 商家端 | `/merchant` |
| 配送端 | `/delivery` |
| 管理后台 | `/admin` |

---

## 五、为什么不会遇到跨域问题

这是本方案的关键设计，理解它才能理解为什么后端不用改配置。

```text
浏览器 ──→ https://<名称>-5173.app.github.dev   （只访问这一个地址）
                    │
                    ▼
            Vite dev server（容器内，监听 0.0.0.0:5173）
                    │  代理 /api、/open-api、/public
                    ▼
            Spring Boot（容器内，127.0.0.1:8080）
```

前端与后端**运行在同一个容器内**，浏览器只访问 5173，由 Vite 把接口请求转发给容器内的 8080。从浏览器的同源策略角度看，所有请求的来源都是 5173，因此**不产生跨域**，后端的 CORS 白名单（当前仅允许 `http://localhost:5173`）无需改动。

与之对应，`.devcontainer/devcontainer.json` 中刻意把端口可见性设为：

```json
"5173": { "visibility": "public" },    ← 前端公开，供你打开
"8080": { "visibility": "private" }    ← 后端私有，不直接暴露
```

如果把 8080 也设为公开、并让前端直连它，就会立刻触发 CORS 错误。

---

## 六、日常使用

### 暂停与恢复

| 操作 | 数据是否保留 | 恢复方式 |
| --- | --- | --- |
| **Stop**（停止） | ✅ 保留 | 重新打开后执行 `bash .devcontainer/dev.sh` |
| **Delete**（删除） | ❌ 清空 | 重新 Create，setup 脚本会重建一切 |

停止方式：Codespaces 页面点 `Stop`，或在 VS Code 中 `F1` → `Codespaces: Stop Current Codespace`。

### 常用命令

```bash
# 查看后端日志（排查启动失败必看）
tail -f work/boot-8080.log

# 查看前端日志
tail -f work/vite-5173.log

# 检查后端健康状态
curl http://127.0.0.1:8080/actuator/health

# 查看服务是否在运行
ss -ltn | grep -E ':(8080|5173)'

# 停止应用（保留数据库）
pkill -f spring-boot:run; pkill -f vite

# 重启应用
bash .devcontainer/dev.sh

# 检查 MySQL 状态
sudo service mysql status

# 进入数据库命令行
mysql -h 127.0.0.1 -u root -pfreshmart-dev

# 查看当前迁移版本
mysql -h 127.0.0.1 -u root -pfreshmart-dev \
  -e "SELECT version, description, success FROM freshmart.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

### 运行测试

```bash
cd backend
# 单元测试（含 10 项一致性实验，需要数据库）
mvn test

# 端到端测试（需后端已在 8080 运行）
cd ..
pwsh -File backend/scripts/e2e-smoke.ps1   # 容器内可能需先安装 PowerShell
```

> 一致性实验类在没有 `DB_PASSWORD` 时自动跳过；容器内已通过 `~/.bashrc` 导出，因此会正常执行。

### 重新初始化数据库

⚠️ 会清空业务数据，仅保留表结构并重建：

```bash
mysql -h 127.0.0.1 -u root -pfreshmart-dev < backend/scripts/bootstrap-database.sql
mysql -h 127.0.0.1 -u root -pfreshmart-dev < backend/scripts/bootstrap-domain-databases.sql
# 然后重启后端，让 Flyway 重新应用迁移
pkill -f spring-boot:run
cd backend && mvn spring-boot:run
```

---

## 七、故障排查

### 容器创建阶段失败

| 现象 | 原因与处理 |
| --- | --- |
| `setup.sh` 报 `apt-get` 失败 | 基础镜像软件源变动。可手动重跑：`bash .devcontainer/setup.sh` |
| `mysql-server` 安装中断 | 重跑 `sudo DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server` |

### 后端启动失败

先看日志：

```bash
tail -n 50 work/boot-8080.log
```

| 日志关键字 | 原因 | 处理 |
| --- | --- | --- |
| `Domain schema is incomplete` | 卫星库缺表 | 重跑建库脚本，再重启后端 |
| `Access denied for user` | 密码不对 | 检查 `echo $DB_PASSWORD`，确认 `~/.bashrc` 已加载 |
| `Communications link failure` | MySQL 未启动 | `sudo service mysql start` |
| `Port 8080 was already in use` | 重复启动 | `pkill -f spring-boot:run` 后重试 |
| `Flyway ... validate failed` | 迁移记录与脚本不一致 | 见下方「迁移冲突」 |

### MySQL 未就绪

```bash
sudo service mysql status
sudo service mysql start
# 若 service 命令不可用，改用：
sudo /etc/init.d/mysql start
```

### 前端页面打不开或空白

| 现象 | 处理 |
| --- | --- |
| 端口面板无 5173 | 确认 `npm run dev` 在跑：`ss -ltn \| grep 5173` |
| 提示 `Blocked request` | Vite 的 `allowedHosts` 未生效，确认 `vite.config.mjs` 含 `.app.github.dev` |
| 页面加载但接口全失败 | 后端没起来，先修后端（见上） |
| 刷新子页面 404 | 属正常现象，重新访问根地址 `/` 即可 |

### 迁移冲突

Flyway 校验失败通常是因为迁移文件与数据库中的记录不一致。开发环境可直接重建：

```sql
-- 谨慎：会丢弃全部业务数据
DROP DATABASE freshmart;
DROP DATABASE freshmart_user;
DROP DATABASE freshmart_merchant;
DROP DATABASE freshmart_trade;
DROP DATABASE freshmart_delivery;
DROP DATABASE freshmart_log;
```

然后重新执行第四节「重新初始化数据库」的命令。

---

## 八、额度与限制

| 项 | 说明 |
| --- | --- |
| 免费额度 | 每个 GitHub 账号每月约 60–120 核心小时（视账号类型） |
| 超时自动停止 | 默认闲置 30 分钟后自动停止，可在设置中调整 |
| 磁盘 | 默认 32 GB，本项目占用较小 |
| 端口 | 转发地址为 `https://<名称>-<端口>.app.github.dev`，随容器变化 |
| 数据持久性 | Stop 保留、Delete 清空 |

**省额度的做法**：演示完即 Stop，而非让它闲置消耗；不要在 Codespaces 里跑长时间压测。

---

## 九、与本地运行的区别

| 项 | 本地运行 | Codespaces |
| --- | --- | --- |
| 环境准备 | 需装 JDK 17、Maven、MySQL 8、Node 20 | 全自动 |
| MySQL 端口 | 默认 3307 | **3306**（脚本已覆盖环境变量） |
| 数据库密码 | 由本机环境变量注入 | 固定为 `freshmart-dev` |
| 访问地址 | `http://localhost:5173` | `https://<名称>-5173.app.github.dev` |
| 后端 CORS | 允许 `localhost:5173` | **无需修改**（同源代理） |
| 数据持久性 | 完全持久 | Stop 保留，Delete 清空 |

> **关于容器内密码**：`freshmart-dev` 是 Codespaces 临时沙箱内的本地开发凭据，仅在容器内部可达，不含任何真实数据。生产数据库、支付密钥与 AI 密钥仍只通过环境变量或本地配置文件注入，不进入版本控制。

---

## 十、配置文件说明

| 文件 | 作用 |
| --- | --- |
| `.devcontainer/devcontainer.json` | 容器定义：镜像、JDK/Node 版本、端口转发与可见性、生命周期钩子 |
| `.devcontainer/setup.sh` | `postCreateCommand`，容器创建时执行一次：装 MySQL、建库、写环境变量、装依赖 |
| `.devcontainer/start-services.sh` | `postStartCommand`，容器每次启动时执行：拉起 MySQL 并等待就绪 |
| `.devcontainer/dev.sh` | 手动调用的一键启动脚本：起前后端并等待健康检查 |

**修改提示**：若需调整 MySQL 版本、Java 版本或增加端口，改 `devcontainer.json` 后需 **Rebuild Container** 才会生效（`F1` → `Codespaces: Rebuild Container`）。

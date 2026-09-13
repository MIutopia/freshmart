# FreshMart 生鲜配送商城

基于 Java 17、Spring Boot 3、Vue 3 的生鲜果蔬配送商城系统。系统按四类业务端组织：系统后台管理、用户、商家、接单配送，并通过独立 AI Gateway 提供可控的智能能力。

## 当前版本

当前为 `v0.1.0` 的后端 MVP 实现阶段，已完成认证、交易、仓配、营销、配送、售后、个人收款码人工核验、经营看板、开放 API 和受控 AI 接口。DeepSeek 仅用于导购与本人订单查询文案生成；个人微信收款码仅限本地测试，真实商户号支付、地图与生产部署仍未接入。

## 目录

```text
backend/                    Spring Boot 服务
  src/main/java/.../api      健康检查与 API 入口占位
  src/main/resources/        应用配置
frontend/                   Vue 3 + Vite 前端壳
docs/                       架构、规则、接口与决策文档
infra/                      本地依赖与部署配置
```

## 本地启动

后端要求 Java 17 和 Maven 3.9+：

```bash
cd backend
mvn spring-boot:run
```

前端要求 Node.js 20+：

```bash
cd frontend
npm install
npm run dev
```

## 开发原则

- 业务说明、状态机和待确认规则统一维护在 `docs/`，源代码只保留必要注释。
- 所有写操作经过认证、授权和审计；AI 只能访问被授权的业务数据。
- 订单、库存、支付和退款使用幂等键，状态变更保留流水。
- 每个可交付阶段创建 Git commit；当前后续功能直接提交到活动分支，完整验证后再合并 `main`。远程仓库为 `https://github.com/MIutopia/freshmart`。
- 本机 MySQL 默认端口为 `3307`，可通过 `DB_URL` 覆盖连接地址。
- 数据库密码和个人微信收款码地址只通过本地环境变量注入；模型密钥只通过 `backend/config/application-local.yml` 注入；两类本地配置均不提交到 Git。
- 首期只建设网页端：用户 `/app`、商家 `/merchant`、配送 `/delivery`、管理后台 `/admin`。

## 下一步确认

首期 P0 规则已确认。DeepSeek 调用前需在 `backend/config/application-local.yml` 配置 `ai.deepseek.api-key`，该本地配置文件不得提交到 Git。

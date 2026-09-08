# FreshMart 生鲜配送商城

基于 Java 17、Spring Boot 3、Vue 3 的生鲜果蔬配送商城系统。系统按四类业务端组织：系统后台管理、用户、商家、接单配送，并通过独立 AI Gateway 提供可控的智能能力。

## 当前版本

当前为 `v0.1.0` 架构骨架，已完成目录、模块边界、接口草案和开发约定，尚未接入真实数据库、支付、地图或大模型供应商。

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
- 每个可交付阶段创建 Git commit，远程 GitHub 仓库待用户提供地址后再绑定和推送。

## 下一步确认

请优先确认 `docs/待确认问题.md` 中的 P0 问题。它们会直接影响数据库模型、订单状态和结算实现。

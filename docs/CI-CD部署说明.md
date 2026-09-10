# CI/CD 与部署说明

## 架构结论

FreshMart 当前采用前后端分离的模块化单体架构。Vue 3 + Vite 前端构建为静态文件，Spring Boot 3 + Java 17 后端构建为可执行 JAR，MySQL 的版本化结构由 Flyway 迁移管理。

当前不适合直接拆成多个独立部署服务：日订单目标为 50 单、并发目标为 20 人，订单、库存、支付和履约仍处于同一事务边界内。首期应部署为一个后端进程和一组静态前端文件；Redis、对象存储、AI Gateway 和真实支付适配器在实际接入后再独立部署。

## GitHub Actions 流程

工作流位于 `.github/workflows/ci-cd.yml`。

| 触发条件 | 后端 | 前端 | 部署 |
| --- | --- | --- | --- |
| 向 `main` 发起 Pull Request | Java 17 执行 `mvn verify`，在临时 MySQL 8 中执行 Flyway 迁移并检查健康端点 | Node 20 执行 `npm ci`、`npm run build` | 不部署 |
| 推送到 `main` | 构建、测试、归档可执行 JAR | 构建、归档静态文件 | 仅在生产环境变量启用时执行 SSH 部署 |
| 手动运行 | 构建、测试、归档 | 构建、归档 | 不部署 |

部署 Job 使用 GitHub Environment `production`，并以 `DEPLOY_ENABLED=true` 作为显式开关。未设置该变量时，推送 `main` 只会执行构建和测试。

## GitHub 配置

在仓库 Settings 的 Environments 中创建 `production`。建议配置审批人后再启用部署。设置以下 Secrets，不要将其写入项目文件：

| Secret | 用途 |
| --- | --- |
| `DEPLOY_HOST` | Linux 部署服务器地址 |
| `DEPLOY_USER` | 用于发布的非 root SSH 用户 |
| `DEPLOY_SSH_KEY` | 发布用户的私钥 |
| `DEPLOY_KNOWN_HOSTS` | 服务器 SSH 主机公钥指纹记录，使用 `ssh-keyscan -H <host>` 获取 |

在同一 Environment 配置以下 Variables：

| Variable | 示例 | 用途 |
| --- | --- | --- |
| `DEPLOY_ENABLED` | `true` | 是否允许 `main` 自动部署 |
| `DEPLOY_PATH` | `/opt/freshmart` | 发布根目录，必须为绝对路径 |
| `DEPLOY_SERVICE` | `freshmart` | systemd 服务名 |
| `DEPLOY_PORT` | `22` | SSH 端口，可选 |
| `DEPLOY_HEALTH_URL` | `http://127.0.0.1:8080/actuator/health` | 服务重启后的健康检查地址，可选 |

## 服务器前置条件

服务器需要 Java 17、`tar`、`curl`、Nginx 和一个具有发布目录写权限的 SSH 用户。该用户还需要被授予仅重启 FreshMart 服务的免密 sudo 权限，例如：

```text
deployuser ALL=(root) NOPASSWD: /bin/systemctl restart freshmart
```

systemd 服务应始终从当前软链接启动 JAR：

```ini
[Service]
User=freshmart
WorkingDirectory=/opt/freshmart/current
EnvironmentFile=/opt/freshmart/shared/freshmart.env
ExecStart=/usr/bin/java -jar /opt/freshmart/current/backend.jar
Restart=always
```

`/opt/freshmart/shared/freshmart.env` 保存 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、AI 密钥及真实支付密钥。该文件不属于 GitHub Actions 上传产物，也不得提交到 Git。

Nginx 应将静态资源指向同一个 `current` 软链接，并将 API 转发到本机后端，例如：

```nginx
server {
    listen 80;
    server_name example.com;
    root /opt/freshmart/current/frontend;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

部署流程会上传到 `/opt/freshmart/releases/<commit-sha>`，解压前端资源，原子更新 `/opt/freshmart/current` 软链接，重启 systemd 服务并调用健康检查。回滚时将 `current` 软链接指向上一个 release 后重启服务即可。

#!/usr/bin/env bash
#
# 一键启动后端与前端，用于 Codespaces 中快速把系统跑起来。
#
#   bash .devcontainer/dev.sh
#
# 后端在 8080，前端在 5173。前端 dev 服务器通过 Vite 代理把 /api、/open-api、/public
# 转发到本机 8080，因此浏览器只需访问 5173 这一个端口，不存在跨域问题。
#
# 日志分别写到 work/boot-8080.log 与 work/vite-5173.log，便于排查启动失败原因。
#
set -euo pipefail

WORKSPACE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${WORKSPACE}/work"
mkdir -p "${LOG_DIR}"

# 加载数据源环境变量（由 setup.sh 写入）
set -a
# shellcheck disable=SC1090
source ~/.bashrc
set +a

if [ -z "${DB_URL:-}" ]; then
    echo "!! 未找到 DB_URL，请先执行 bash .devcontainer/setup.sh" >&2
    exit 1
fi

# 依赖服务
if ! pgrep -x mysqld >/dev/null 2>&1; then
    echo "==> 启动 MySQL"
    sudo service mysql start
    sleep 3
fi

# 端口占用检查：避免重复启动把端口冲突伪装成启动失败
for port in 8080 5173; do
    if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":${port} "; then
        echo "!! 端口 ${port} 已被占用，请先停止占用进程" >&2
        exit 1
    fi
done

echo "==> 启动后端（日志：work/boot-8080.log）"
(cd "${WORKSPACE}/backend" && nohup mvn -B spring-boot:run > "${LOG_DIR}/boot-8080.log" 2>&1 &)

echo "==> 启动前端（日志：work/vite-5173.log）"
(cd "${WORKSPACE}/frontend" && nohup npm run dev > "${LOG_DIR}/vite-5173.log" 2>&1 &)

echo "==> 等待后端完成 Flyway 迁移与结构校验（首次约需 60–90 秒）"
ready=0
for _ in $(seq 1 60); do
    if curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 3
done

if [ "${ready}" -eq 1 ]; then
    echo ""
    echo "后端就绪：http://127.0.0.1:8080/actuator/health"
    echo "前端页面：请打开「端口」面板中的 5173 公网地址"
    echo ""
    echo "测试账号：admin-test-01 / merchant-test-01 / consumer-test-01 / rider-test-01，密码均为 password"
else
    echo ""
    echo "!! 后端在 180 秒内未就绪，最后 40 行日志如下：" >&2
    tail -n 40 "${LOG_DIR}/boot-8080.log" >&2 || true
    exit 1
fi

#!/usr/bin/env bash
#
# Codespaces 每次启动容器时执行：确保 MySQL 在运行。
# 只负责把依赖服务拉起来，不自动启动应用——应用日志需要留在终端里观察。
#
set -euo pipefail

if ! pgrep -x mysqld >/dev/null 2>&1; then
    echo "==> 启动 MySQL"
    sudo service mysql start
fi

for _ in $(seq 1 30); do
    if mysqladmin ping --silent 2>/dev/null || sudo mysqladmin ping --silent 2>/dev/null; then
        echo "==> MySQL 已就绪（监听 3306）"
        exit 0
    fi
    sleep 1
done

echo "!! MySQL 未能在 30 秒内就绪，请手动检查：sudo service mysql status" >&2
exit 1

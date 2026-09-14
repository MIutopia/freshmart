#!/usr/bin/env bash
#
# Codespaces 首次创建容器时执行：安装 MySQL 8、初始化六个数据库、准备前后端依赖。
#
# 关于此处的数据库密码：它是 Codespaces 临时沙箱内的本地开发凭据，仅在容器内部可达，
# 不含任何真实数据或生产密钥。仓库的正式凭据（生产数据库、支付密钥、AI 密钥）仍然只
# 通过环境变量或本地配置文件注入，不进入版本控制。
#
set -euo pipefail

DB_PASSWORD="freshmart-dev"
MYSQL_PORT="3306"
WORKSPACE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> 1/5 安装 MySQL 8"
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq mysql-server

echo "==> 2/5 启动 MySQL 并设置 root 密码"
sudo service mysql start
# 等待 socket 就绪，避免紧接着执行 SQL 时连接被拒
for _ in $(seq 1 30); do
    if sudo mysqladmin ping --silent 2>/dev/null; then
        break
    fi
    sleep 1
done
sudo mysql <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED WITH caching_sha2_password BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL

echo "==> 3/5 创建六个数据库（主库 + 五个卫星库）"
mysql -h 127.0.0.1 -P "${MYSQL_PORT}" -u root -p"${DB_PASSWORD}" \
    < "${WORKSPACE}/backend/scripts/bootstrap-database.sql"
mysql -h 127.0.0.1 -P "${MYSQL_PORT}" -u root -p"${DB_PASSWORD}" \
    < "${WORKSPACE}/backend/scripts/bootstrap-domain-databases.sql"

echo "==> 4/5 写入数据源环境变量到 ~/.bashrc"
# 应用默认连 3307，而容器内 MySQL 监听 3306，因此六个数据源都需要显式指向 3306
MARKER="# FreshMart Codespaces datasource"
if ! grep -q "${MARKER}" ~/.bashrc 2>/dev/null; then
    cat >> ~/.bashrc <<ENV

${MARKER}
export DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
export DB_USERNAME="root"
export DB_PASSWORD="${DB_PASSWORD}"
export USER_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart_user?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
export MERCHANT_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart_merchant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
export DELIVERY_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart_delivery?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
export TRADE_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart_trade?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
export LOG_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/freshmart_log?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci"
ENV
fi
# 让当前 shell 也立即生效
set -a
# shellcheck disable=SC1090
source ~/.bashrc
set +a

echo "==> 5/5 安装前后端依赖"
(cd "${WORKSPACE}/frontend" && npm install --no-audit --no-fund)
(cd "${WORKSPACE}/backend" && mvn -q -B -DskipTests dependency:go-offline)

echo ""
echo "初始化完成。表结构将由后端启动时的 Flyway 迁移自动创建。"
echo "启动方式见 README 的「在 GitHub Codespaces 中运行」一节。"

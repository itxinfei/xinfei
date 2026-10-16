#!/bin/bash
# 一次性确保 xinfei 库存在（幂等，重复执行不丢数据）。
# 建表与最小种子数据由应用启动期的 Flyway 负责：
#   V1 = 全量表结构基线，V2 = admin/123456 等最小可登录数据
#   （src/main/resources/db/migration/，基线语义见 docs/FLYWAY_MIGRATION.md）。
# 本容器不再导入任何 SQL 脚本，避免与 Flyway 形成两个结构来源。
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

MYSQL_CMD=(mysql -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4)
DB=xinfei

echo "[db-init] waiting for mysql ..."
for i in $(seq 1 60); do
  if mysqladmin ping -h mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "[db-init] mysql not ready after 60s" >&2
    exit 1
  fi
  sleep 1
done

echo "[db-init] ensuring database ${DB} (utf8mb4)"
"${MYSQL_CMD[@]}" -e "CREATE DATABASE IF NOT EXISTS ${DB} CHARACTER SET utf8mb4;"

echo "[db-init] done (schema 交给 Flyway)"

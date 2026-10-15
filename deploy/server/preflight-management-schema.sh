#!/usr/bin/env bash
set -euo pipefail

# Read-only gate to run BEFORE replacing the Management JAR.
# Authentication uses local socket credentials or an existing MySQL option file.
usage() {
    echo "用法: $0 <数据库名> [MySQL defaults-extra-file]" >&2
    exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
database=$1
[[ $database =~ ^[A-Za-z][A-Za-z0-9_-]{0,63}$ ]] || usage

if [[ -n ${TINCLINK_MYSQL_BIN:-} ]]; then
    mysql_bin=$TINCLINK_MYSQL_BIN
else
    mysql_bin=
    for candidate in /usr/bin/mysql /usr/bin/mariadb /www/server/mysql/bin/mysql; do
        if [[ -x $candidate ]]; then mysql_bin=$candidate; break; fi
    done
fi
[[ $mysql_bin == /* && -x $mysql_bin ]] || { echo '预检失败：未找到可执行的 MySQL 客户端' >&2; exit 2; }

mysql_options=()
if [[ $# -eq 2 ]]; then
    defaults_file=$2
    [[ $defaults_file == /* && -f $defaults_file && -r $defaults_file && ! -L $defaults_file ]] || {
        echo '预检失败：MySQL 凭据文件必须是可读的绝对路径普通文件' >&2
        exit 2
    }
    # MySQL requires --defaults-extra-file to be its first option.
    mysql_options+=("--defaults-extra-file=$defaults_file")
fi
mysql_options+=(--batch --skip-column-names --connect-timeout=5 "--database=$database")

query() {
    "$mysql_bin" "${mysql_options[@]}" "--execute=$1"
}

fail() {
    echo "预检失败：$1；保持旧 JAR 运行，先审查 sql/20261011_tinc_resource_grants.sql" >&2
    exit 1
}

actual_database=$(query 'SELECT DATABASE()') || fail '无法连接目标数据库'
[[ $actual_database == "$database" ]] || fail '实际连接的数据库与指定名称不一致'

engine=$(query "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tinc_resource_grant'") || fail '无法读取授权表元数据'
[[ $engine == InnoDB ]] || fail '授权表缺失或存储引擎不正确'

columns=$(query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tinc_resource_grant' AND ((COLUMN_NAME = 'user_id' AND DATA_TYPE = 'bigint' AND IS_NULLABLE = 'NO') OR (COLUMN_NAME = 'resource_type' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 16 AND IS_NULLABLE = 'NO') OR (COLUMN_NAME = 'resource_id' AND DATA_TYPE = 'bigint' AND IS_NULLABLE = 'NO') OR (COLUMN_NAME = 'granted_by' AND DATA_TYPE = 'bigint' AND IS_NULLABLE = 'NO') OR (COLUMN_NAME = 'granted_at' AND DATA_TYPE = 'timestamp' AND IS_NULLABLE = 'NO'))") || fail '无法读取授权表列定义'
[[ $columns == 5 ]] || fail '授权表必需列缺失或类型不匹配'

primary_key=$(query "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tinc_resource_grant' AND INDEX_NAME = 'PRIMARY'") || fail '无法读取授权表主键'
[[ $primary_key == 'user_id,resource_type,resource_id' ]] || fail '授权表联合主键不匹配'

resource_index=$(query "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tinc_resource_grant' AND INDEX_NAME = 'idx_tinc_grant_resource'") || fail '无法读取授权表索引'
[[ $resource_index == 'resource_type,resource_id' ]] || fail '授权表资源索引不匹配'

# Match the application startup probe. A result of zero is expected for any row count.
schema_probe=$(query 'SELECT COUNT(user_id) + COUNT(resource_type) + COUNT(resource_id) + COUNT(granted_by) + COUNT(granted_at) FROM tinc_resource_grant WHERE 1 = 0') || fail '应用启动所需的授权表查询失败'
[[ $schema_probe == 0 ]] || fail '应用启动所需的授权表查询结果异常'

echo "预检通过：database=$database，tinc_resource_grant 结构与启动查询可用；尚未替换 JAR"

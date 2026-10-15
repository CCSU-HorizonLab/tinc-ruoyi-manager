#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
preflight=$script_dir/../preflight-management-schema.sh
temporary_dir=$(mktemp -d)
trap 'rm -f "$temporary_dir/mysql"; rmdir "$temporary_dir"' EXIT
cp "$script_dir/mock-mysql.sh" "$temporary_dir/mysql"
chmod 700 "$temporary_dir/mysql"

export TINCLINK_MYSQL_BIN="$temporary_dir/mysql"
MOCK_SCHEMA_STATE=valid bash "$preflight" ry-tinc >/dev/null

for state in connection_error missing wrong_engine invalid_columns invalid_index broken_probe; do
    if MOCK_SCHEMA_STATE=$state bash "$preflight" ry-tinc >/dev/null 2>&1; then
        echo "失败：$state 应阻止部署" >&2
        exit 1
    fi
done
if MOCK_DATABASE=other bash "$preflight" ry-tinc >/dev/null 2>&1; then
    echo '失败：错误数据库应阻止部署' >&2
    exit 1
fi
if bash "$preflight" 'ry-tinc;DROP' >/dev/null 2>&1; then
    echo '失败：非法数据库名应被拒绝' >&2
    exit 1
fi
echo 'Management 数据库预检测试通过'

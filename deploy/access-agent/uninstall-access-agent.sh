#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 root 执行卸载脚本" >&2
  exit 1
fi

systemctl disable --now tinclink-access-agent.service 2>/dev/null || true
rm -f /etc/systemd/system/tinclink-access-agent.service
rm -f /opt/tinclink/tinclink-access-agent.jar
systemctl daemon-reload
systemctl reset-failed tinclink-access-agent.service 2>/dev/null || true

echo "Access Agent 已卸载。/etc/tinc、Tinc 私钥及 /etc/tinclink Agent 配置均已保留。"

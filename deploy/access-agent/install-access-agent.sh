#!/bin/sh
set -eu

JAR_SOURCE=${1:-./tinclink-access-agent.jar}
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 root 执行安装脚本" >&2
  exit 1
fi
if [ ! -f "$JAR_SOURCE" ]; then
  echo "找不到 Access Agent JAR" >&2
  exit 1
fi
if [ "${AGENT_ID:-}" = "" ] || ! printf '%s' "$AGENT_ID" | grep -Eq '^[A-Za-z0-9_.-]{1,64}$'; then
  echo "请通过 AGENT_ID 提供合法的 Agent ID" >&2
  exit 1
fi
if [ "${AGENT_SECRET:-}" = "" ] || [ "${#AGENT_SECRET}" -lt 32 ] || [ "${#AGENT_SECRET}" -gt 512 ] || ! printf '%s' "$AGENT_SECRET" | grep -Eq '^[A-Za-z0-9._~-]+$'; then
  echo "请通过 AGENT_SECRET 提供 32-512 位、仅含安全字符的随机密钥" >&2
  exit 1
fi
AGENT_PORT=${AGENT_PORT:-9088}
case "$AGENT_PORT" in
  *[!0-9]*|'') echo "AGENT_PORT 必须是 1-65535 的整数" >&2; exit 1 ;;
esac
if [ "$AGENT_PORT" -lt 1 ] || [ "$AGENT_PORT" -gt 65535 ]; then
  echo "AGENT_PORT 必须是 1-65535 的整数" >&2
  exit 1
fi
for command_name in java tincd systemctl firewall-cmd; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少运行依赖: $command_name" >&2
    exit 1
  fi
done

install -d -m 0755 /opt/tinclink /etc/tinclink
install -m 0644 "$JAR_SOURCE" /opt/tinclink/tinclink-access-agent.jar
if [ ! -f /etc/tinclink/access-agent.yml ]; then
  install -m 0644 "$SCRIPT_DIR/access-agent.yml.example" /etc/tinclink/access-agent.yml
fi
install -m 0644 "$SCRIPT_DIR/tinclink-access-agent.service" /etc/systemd/system/tinclink-access-agent.service
umask 077
printf 'AGENT_ID=%s\nAGENT_PORT=%s\nAGENT_SECRET=%s\n' "$AGENT_ID" "$AGENT_PORT" "$AGENT_SECRET" > /etc/tinclink/access-agent.env
chmod 0600 /etc/tinclink/access-agent.env
systemctl daemon-reload
systemctl enable --now tinclink-access-agent.service
systemctl --no-pager --full status tinclink-access-agent.service

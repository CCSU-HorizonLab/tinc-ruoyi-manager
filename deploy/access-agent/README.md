# TincLink Access Agent 部署

该目录是 Access Agent 的通用 Linux 部署入口，适用于 systemd-nspawn/LXC 容器、Ubuntu/RHEL 系 ECS、VM 和物理 Linux。脚本本身不依赖或调用任何容器命令。

## 系统要求

- systemd；
- Java 17 或兼容 Java Runtime；
- Tinc 1.0.x；
- firewalld；
- iproute2、procps、curl；
- 可用的 `/dev/net/tun`。

Debian/Ubuntu 可安装 `openjdk-17-jre-headless tinc firewalld iproute2 procps curl`；RHEL/OpenAnolis/Alibaba Cloud Linux 可安装 `java-17-openjdk-headless tinc firewalld iproute procps-ng curl`。

## 安装

将本目录与构建得到的 `tinclink-access-agent.jar` 放到同一临时目录，使用安全随机源为每台 Access Server 生成独立 Secret：

```sh
chmod 0700 install-access-agent.sh uninstall-access-agent.sh
export AGENT_ID=access-a
export AGENT_PORT=9088
export AGENT_SECRET='使用安全随机源生成的至少32位独立密钥'
./install-access-agent.sh ./tinclink-access-agent.jar
```

脚本不会自动安装系统包，不会修改既有 `/etc/tinc` 网络。Secret 只写入权限为 `0600` 的 `/etc/tinclink/access-agent.env`，不会输出到日志。

systemd-nspawn/LXC 的 namespace、TUN 映射、veth 和 cgroup 限制属于承载层配置，不进入 Management 业务代码。`systemd-nspawn/` 中的文件是本阶段开发环境模板；真实 ECS、VM 或物理机不需要这些模板。

## 网络与健康检查

Agent 默认监听 9088。只应允许 Management Server 的管理地址访问该端口，不要直接向公网开放。

```sh
curl -i http://127.0.0.1:9088/api/v1/health
curl -i -H "Authorization: Bearer $AGENT_SECRET" http://127.0.0.1:9088/api/v1/health
```

无 Token 返回 401，错误 Token 返回 403，正确 Token 返回 200。

## 卸载与回滚

```sh
./uninstall-access-agent.sh
```

卸载只移除 Agent unit 和 JAR，明确保留 `/etc/tinc`、Tinc 私钥、hosts、网络目录和 `/etc/tinclink` 配置。需要彻底清理时必须另行备份并由管理员人工确认。

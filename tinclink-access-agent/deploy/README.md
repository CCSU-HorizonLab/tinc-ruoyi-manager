# TincLink Access Agent 部署

该部署包适用于 systemd-nspawn/LXC 容器、ECS、VM 和物理 Linux，不依赖容器管理命令。

## 系统依赖

- systemd
- Java 17 或兼容的 Java Runtime
- Tinc 1.0.x
- firewalld
- iproute2、procps、curl
- `/dev/net/tun`

## 安装

将本目录与 `tinclink-access-agent.jar` 放到同一临时目录，生成独立 Secret 后执行：

```sh
export AGENT_ID=access-a
export AGENT_PORT=9088
export AGENT_SECRET='使用安全随机源生成的至少32位独立密钥'
./install-access-agent.sh ./tinclink-access-agent.jar
```

安装脚本不会安装系统软件包，也不会修改 `/etc/tinc` 中已有网络。Agent 以 root 运行是因为它需要受白名单限制地管理 Tinc 配置、systemd、接口和 firewalld；API 不提供通用命令执行入口。

## 验证

```sh
systemctl status tinclink-access-agent
curl -i http://127.0.0.1:9088/api/v1/health
curl -i -H "Authorization: Bearer $AGENT_SECRET" http://127.0.0.1:9088/api/v1/health
```

无 Token 应返回 401，错误 Token 应返回 403，正确 Token 应返回 200。生产环境应仅允许 Management 地址访问 Agent 端口。

## 卸载

```sh
./uninstall-access-agent.sh
```

卸载只移除 Agent unit 和 JAR，保留 `/etc/tinclink`、全部 Tinc 私钥、网络配置和 `/etc/tinclink` 下的 Agent 配置，方便审计和回滚。

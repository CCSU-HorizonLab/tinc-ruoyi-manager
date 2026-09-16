# Tinc 运行面管理与部署契约

更新日期：2026-09-16。

## 成功语义

`LocalTincRuntimeManager` 是服务端配置面和运行数据面的统一入口。每个网络由独立 `ReentrantLock` 串行化；相同网络的并发 ensure、hosts 写入和 HUP 不会交错。

`ensureNetworkReady(netName)` 按以下顺序幂等执行：

1. 严格校验网络名，并用词法路径和真实路径双重检查把文件限制在配置根目录中；网络目录和关键配置/密钥/脚本不得为符号链接。
2. 检查 `tinc.conf`、`hosts/server_master`、非空 `rsa_key.priv`、可选的 `tinc-up/tinc-down` 权限和 `sh -n` 语法。
3. 校验 `Name=server_master`、`Mode=router`、Interface（Linux ≤15 字符）、一致且有效的主配置/host Port、服务端 `/32` VPN 地址以及 RSA 公钥块。
4. 遍历其他 Tinc 配置，拒绝重复 Interface/Port；检查现有 TCP/UDP socket 的 PID，不会 kill 未知进程。
5. firewalld 同时 query/add/query 运行时和永久 TCP/UDP 规则，不删除任何已有规则。firewalld 未运行时识别 nftables/iptables 并返回 `TINC_FIREWALL_FAILED`，不会假装已管理。
6. 未运行时执行参数化的 `systemctl enable --now tinc@<net>.service`；已运行时不 restart。
7. 轮询并重新读取真实状态，直至 systemd active、MainPID>0、接口存在、服务端 VPN IP 正确、TCP/UDP 均监听。超时后再读一次，防止“命令超时但服务稍后成功”的误判。

只有全部成立，`readiness` 才是 `READY`。

## 客户端 API

- `POST /api/tinc/client/config/download`：认证会话后先 ensure；非 READY 返回 HTTP 503 JSON，READY 才返回 ZIP。
- `POST /api/tinc/client/key/upload`：校验 sid/token、精确 `Subnet=<node_ip>/32` 与 PKCS#1 RSA；同目录临时文件写入、fsync、原子 rename、0600；内容变化才 HUP，不 restart；随后校验 PID、hosts SHA-256 和 READY，最后返回 `text/plain` 的 `server_master`。失败时恢复旧 hosts 并再次 HUP；相同内容幂等成功且不替换文件，因此 SHA、mtime、PID 与 HUP 计数保持不变。
- `GET /tinc/network/runtime/{netName}`：需要后台 query 权限，返回运行状态布尔量和脱敏故障信息，不返回密钥、Token 或敏感路径。

运行状态字段：`netName`, `systemdEnabled`, `systemdActive`, `mainPidPresent`, `interfaceName`, `interfacePresent`, `expectedVpnIp`, `actualVpnIp`, `port`, `tcpListening`, `udpListening`, `configValid`, `privateKeyPresent`, `lastReloadResult`, `readiness`, `failureCode`, `failureMessage`。

稳定业务码：`TINC_CONFIG_INVALID`, `TINC_PRIVATE_KEY_MISSING`, `TINC_SCRIPT_INVALID`, `TINC_INTERFACE_CONFLICT`, `TINC_PORT_CONFLICT`, `TINC_SERVICE_START_FAILED`, `TINC_SERVICE_INACTIVE`, `TINC_RELOAD_FAILED`, `TINC_INTERFACE_NOT_READY`, `TINC_TCP_NOT_LISTENING`, `TINC_UDP_NOT_LISTENING`, `TINC_FIREWALL_FAILED`, `TINC_RUNTIME_NOT_READY`, `CLOUD_FIREWALL_SUSPECTED`。运行面失败的客户端接口使用 HTTP 503，不再返回 HTTP 200 假成功。

## 接口与端口策略

已有网络从自己的 `tinc.conf` 读取并保留 Interface，因此生产 `new_network` 继续使用 `tinc0`。新网络第一次生成配置时使用 `tn` + `SHA-256(netName)` 前 12 个十六进制字符，结果稳定且不超过 Linux 15 字符限制。Interface 与 Port 都在创建/ensure 前和其他配置交叉验证；冲突分别返回 `TINC_INTERFACE_CONFLICT`、`TINC_PORT_CONFLICT`。普通客户端请求永远不会停止别的网络或未知进程。

## 命令与权限边界

默认执行器只接受固定绝对路径和每个操作的完整参数形状白名单，并使用 `ProcessBuilder(List<String>)`，不经过 shell；命令最长 60 秒、输出最多 32 KiB，检查退出码且日志只记录 executable/参数个数。网络名、节点名、接口、端口和路径均有白名单，拒绝 `sh -c`、任意 systemctl 动作和越界端口。

`deploy/server/ruoyi-tinc-runtime` 是可选的 root 固定 helper。它只接受 systemctl 状态/enable、指定网络 HUP、ip/ss 只读、firewalld 指定 TCP/UDP 端口和脚本语法检查；拒绝 shell 片段。切换非 root Java 服务时：

1. helper 安装为 `/usr/local/sbin/ruoyi-tinc-runtime`，root:root 0755；sudoers 使用 `deploy/server/ruoyi-tinc-runtime.sudoers`，root:root 0440 并 `visudo -cf`。
2. 创建 `ruoyi-tinc` 系统用户和专用组；只授予所需部署目录、上传目录与 `/etc/tinc` 网络目录的目录写权限，私钥仍保持 0600，不开放任意 shell/root 命令。
3. 设置 `tinc.runtime.use-helper=true`、`helper-use-sudo=true`，service `User/Group` 改为该用户。先在隔离网络验收再切生产。

当前部署单元 `deploy/server/ruoyi-tinc-backend.service` 保持现网 root 身份以避免一次部署同时引入目录 ACL 风险，但加上 `NoNewPrivileges`, `ProtectHome`, `ProtectSystem=full`, `PrivateTmp` 和精确 `ReadWritePaths`。这不是对 root 的永久假设；helper/非 root 迁移路径已固化。

## firewalld 与云安全组

后端能证明本机 firewalld 与本地监听，不能在没有阿里云 API 凭据时修改安全组。部署前必须确认每个 Tinc Port 的 TCP/UDP 入方向规则。若本机监听和 firewalld 都正常而独立外部探测失败，应诊断为 `CLOUD_FIREWALL_SUSPECTED`；不能声称后端已修改云安全组。

## 部署与回滚

部署前备份 jar、外部 application.yml 和 systemd 单元；不备份或复制任何私钥内容到日志。上传到临时文件，校验 SHA-256 后原子替换。`systemctl daemon-reload` 后启动新 backend unit，并立刻验证 HTTP、日志、`new_network` PID/接口/IP/TCP/UDP/firewalld；不得停止 `new_network` 做故障测试。

回滚时原子恢复上一版 jar/application/unit，`daemon-reload` 后 restart 后端；随后重复同一只读验证。后端回滚不停止 Tinc 网络，`tinc@new_network.service` 应继续 active。

## 2026-09-15 至 2026-09-16 生产部署记录

- 当前 `ruoyi-admin.jar` SHA-256：`3c463b890edd76255900edf7ef7af7b583ac41d62869b0aa7b0ecf9686f7d9f8`；上一版保留为 `/www/wwwroot/ry-server-tinc/ruoyi-admin.jar.bak-20260916-0853-hardening`。
- 新后端单元：`ruoyi-tinc-backend.service`，已 enabled/active；2026-09-16 最后部署后 `MainPID=2210211`、`NRestarts=0`、本机 8081 和公网 83 均返回 HTTP 200。
- 旧的损坏单元 `spring_ruoyi_admin_jar.service` 已 disabled；原文件备份为 `/usr/lib/systemd/system/spring_ruoyi_admin_jar.service.bak-20260915-1554`。
- JAR 与外部配置备份：`/www/wwwroot/ry-server-tinc/ruoyi-admin.jar.bak-20260915-1554`、`application.yml.bak-20260915-1554`；后续构造器/提权边界修复前也各保留了单独 JAR 备份。
- Maven 测试：17 tests，0 failures，0 errors，1 skipped；跳过项仅因 Windows 测试环境无法创建符号链接，服务器部署前已完成真实文件类型检查。覆盖相同内容 mtime、完整命令参数白名单、路径逃逸及符号链接边界。
- 部署后未停止或重启 `new_network`：其 PID 保持 `2183848`；`tinc0=10.0.11.1/24`；TCP/UDP 600 都由该 PID 监听；firewalld 运行时和永久 `600/tcp`、`600/udp` 均为 yes；Windows 到公网 TCP 600 探测成功。
- 隔离网络 `network1` 的 PID 保持 `2203449`、`NRestarts=0`；相同公钥重复和两个并发上传均未 HUP。Windows Agent 连续 5 轮启动/连通/停止/清理全部通过，平均连接耗时 8851 ms；Agent 服务重启后也能从持久化配置恢复并再次连通。
- 最后部署后用 `scripts/tinc/server/smoke-backend-runtime.py` 验证 login/config/key-upload 均为 HTTP 200；ZIP 契约不变，相同公钥上传后 SHA、mtime 与 PID 均未变化。遗留状态上报和心跳接口已改为 sid+Token 绑定，伪 Token 返回 HTTP 401。
- `rsa_key.priv` 只验证为存在、root:root、0600，未读取或输出内容；`tinc-up/down` 语法通过；`hosts/new_client` 只验证存在、owner/mode/长度，未输出公钥。

回滚命令应由管理员按实际备份版本执行：先把目标备份复制为同目录临时文件，再原子替换 JAR/外部配置；恢复旧 unit 时先停止并 disable 新 unit，再恢复旧 unit、`daemon-reload` 和启动后端。回滚过程中不要停止 `tinc@new_network.service`，完成后再次核验其 PID、接口、地址、TCP/UDP 和 firewalld。

# Tinc 新客户端后端公钥交换与运行契约

更新时间：2026-09-09（北京时间）。本文依据当前后端源码、本机运行于 8081 的重新构建
版本、Redis、数据库中的隔离测试节点和后台本地 Tinc 副本完成核查。本文不记录密码、
Token、私钥、完整真实公钥或生产地址。

## 1. `POST /api/tinc/client/key/upload`

### 请求与认证

请求必须为 `POST`，`Content-Type` 必须是 `application/json`（允许 UTF-8 charset 参数）。
Authorization Header 和 Cookie 均不是本接口契约。请求 JSON 必须包含：

```json
{
  "sid": "<登录响应中的 sid>",
  "token": "<登录响应中的 token>",
  "action": "exchangeFile",
  "content": "Subnet = <该节点 network_ip>/32\n\n-----BEGIN RSA PUBLIC KEY-----\n<PKCS#1 RSA 公钥>\n-----END RSA PUBLIC KEY-----"
}
```

后端对 `sid` 做精确匹配，不接受数据库查询中的模糊匹配结果。数据库允许不同历史/网络
记录复用同一个 sid，因此登录以 `sid + password` 唯一定位节点；登录生成 32 字节随机
Token，Redis 只保存 `SHA-256(token)` 对应的节点数据库 ID，TTL 为 30 分钟，不保存
Token 明文。上传和下载均从所有精确 sid 记录中选择 Token 绑定的节点 ID，不会取模糊查询
第一条。旧客户端只传 `sid` 会失败，不提供不安全兼容分支。

接口没有额外的角色/ACL“无权限”状态。此接口的授权边界就是有效的 sid/Token 节点绑定：
缺失、失效、过期或跨节点 Token 均返回 HTTP 401 `CLIENT_TOKEN_INVALID`。

`content` 只接受数据库 `tinc_node.network_ip + "/32"` 和一段 2048–8192 位 PKCS#1
`RSA PUBLIC KEY`。RSA 模数必须为正奇数，公钥指数必须是大于等于 3 的奇数。不接受 SPKI
`PUBLIC KEY`、私钥、额外指令、任意子网或路径内容。整个上传请求体上限为 64 KiB。

### 成功响应

成功时返回：

- HTTP 200；
- `Content-Type: text/plain; charset=UTF-8`；
- `Cache-Control: no-store`；
- 正文为 UTF-8 `hosts/server_master`，不是 JSON。

响应正文格式为：

```text
Address = <网关连接地址>
Port = <1-65535，可选且最多一项>
Subnet = <网关虚拟 IPv4>/32

-----BEGIN RSA PUBLIC KEY-----
<2048-8192 位 PKCS#1 RSA 公钥>
-----END RSA PUBLIC KEY-----
```

`Address` 和 `/32` IPv4 `Subnet` 必须各有且仅有一项；`Port` 可省略；除此之外的指令、
重复字段、无效值、PEM 后额外内容、不可解析或位数不合规的 RSA 公钥均不会返回给客户端。

### 错误响应

业务错误均为 `application/json`（可带 `charset=UTF-8`），正文结构为：

```json
{"code":"<业务码>","msg":"<脱敏错误信息>"}
```

| 条件 | HTTP | code |
| --- | ---: | --- |
| Content-Type 不是 JSON | 415 | `CLIENT_CONTENT_TYPE_INVALID` |
| 请求体超过 64 KiB | 413 | `CLIENT_REQUEST_TOO_LARGE` |
| 请求体不是 JSON 对象 | 400 | `CLIENT_REQUEST_INVALID` |
| action 不是 `exchangeFile` | 400 | `CLIENT_ACTION_INVALID` |
| sid 缺失 | 400 | `CLIENT_SID_INVALID` |
| sid 不存在或不能精确匹配 | 404 | `CLIENT_NODE_NOT_FOUND` |
| Token 缺失、失效、过期或与 sid 不匹配 | 401 | `CLIENT_TOKEN_INVALID` |
| Redis 会话服务异常 | 503 | `CLIENT_SESSION_UNAVAILABLE` |
| 节点关联的网络不存在 | 404 | `CLIENT_NETWORK_NOT_FOUND` |
| `hosts/server_master` 缺失、无效或不安全 | 503 | `GATEWAY_HOST_UNAVAILABLE` |
| 网络未关联有效的网关服务器 | 503 | `GATEWAY_UNAVAILABLE` |
| Subnet、公钥格式、类型或位数不合规 | 400 | `CLIENT_PUBLIC_KEY_INVALID` |
| SFTP 推送或网关重载失败 | 502 | `GATEWAY_KEY_APPLY_FAILED` |

网关主机配置在任何客户端公钥写入前完成读取和严格校验，因此缺失或无效时不会产生
`hosts/<sid>` 副作用。

### 写入、重载与幂等性

后端将规范化后的期望内容同时与后台本地 `hosts/<sid>` 及成功应用回执比较：

- host 内容和回执 SHA-256 均相同：直接返回 200，不执行 SFTP、不重载网关、不更新节点记录；
- 只有历史 host、没有成功回执：不得当作幂等成功，仍执行一次远程写入和 HUP；
- 内容变化：SFTP 先在远程同目录写随机临时文件，再 rename 到 `hosts/<sid>`；随后执行
  `tincd -n <netName> -kHUP`；远程重载成功后才用临时文件和原子替换更新后台本地副本，
  再写入仅含规范化内容 SHA-256 的 `.client-key-state/<sid>.sha256` 回执，最后更新节点状态；
- SFTP 异常或远程命令非零退出都会返回 502。此时后台本地旧 `hosts/<sid>` 保持不变，
  且不生成成功回执，后续重试仍会再次尝试远程写入和重载；
- 同一后端 JVM 内，同一网络/节点的“回执检查、远程写入、HUP、本地提交”按锁串行执行，
  防止客户端并发重试导致重复写入或重复重载。

回执只表示后端此前确实完成过远程写入与 HUP，不含 Token、私钥或公钥正文，也不会进入
客户端下载 ZIP。它不是每次请求都读取远程文件的审计操作；多后端实例部署前仍需把该锁
升级为跨实例协调机制。

## 2. 网关从 switch 迁移到 router

2026-09-09 对 `D:/tinc` 后台本地副本复核，仍有 3 个 `Mode = switch` 网络。为避免泄露
实际名称，以下标识为网络名 SHA-256 的前 12 个十六进制字符：

| 脱敏标识 | 本地模式 | 远程迁移状态 |
| --- | --- | --- |
| `gateway-fee7f46dd5c5` | `switch` | 未迁移、远程未验证 |
| `gateway-a7662f44d650` | `switch` | 未迁移、远程未验证 |
| `gateway-59f2b5cbf5a7` | `switch` | 未迁移、远程未验证；当前选定测试网络 |

当前选定测试网关的 TCP/22 仍不可达，不能进入 SSH 认证。因此本轮没有修改真实网关、没有
重载或重启远程 Tinc，也没有取得网关 `tincd --version`。客户端旧发布物的 1.0.36 版本
不能作为网关版本证据。

SSH 恢复后，管理员应先只处理选定测试网络：

1. 先确认 TCP/22、SSH 认证和 SFTP 可用，并读取远程现有配置留作回滚依据；
2. 在后台编辑并保存原网络，不重命名网络；`updateTincNetworkMange()` 会调用
   `createTincConf()` 生成 `Mode = router` 并推送到对应 `/etc/tinc/<netName>/tinc.conf`；
3. 同一保存流程会同步 `hosts/server_master` 和网关脚本，然后执行
   `tincd -n <netName> -kHUP`；远程命令非零退出现在会使后台操作失败；
4. 通过 SSH 只读确认远程 `Mode = router`、`tincd --version`、实际监听端口和运行进程；
5. 用隔离节点完成一次变化公钥上传和一次相同公钥重复上传，再迁移其余两个网关。

这套迁移改变网关运行配置，但不改变客户端下载 ZIP；ZIP 仍只能包含
`<netName>/tinc.conf` 和 `<netName>/hosts/server_master`。

## 3. 网络运行契约

### 已确认部分

- 客户端地址来自 `tinc_node.network_ip`，上传 Subnet 和客户端地址前缀固定为 `/32`；
- 数据库没有独立前缀字段，客户端不得猜 `/24`；
- 网关虚拟地址由 `tinc_network.segment + ".1"` 生成，网关 host 中同样发布为 `/32`；
- 后端没有向客户端下发网卡停止策略的字段，也没有通过 ZIP 下发启停脚本。

### 停止状态

后端能明确地址所有权，但当前没有真实 TAP/TUN 停止测试证据。产品约束是专用网卡和驱动在
停止 VPN 后保留、卸载产品时再移除。待客户端侧确认的确定性空闲状态应为：移除本次后端
分配的 `/32` VPN 地址；Windows 专用 TAP 不切回 DHCP，Linux 删除该地址后将专用接口置为
down。该项是待双方验收的运行契约，不是本轮已经完成的实机结论。

### 连通性目标

代码可推导的候选目标是对应网络的网关虚拟地址 `<segment>.1`，但由于 SSH 不可达，尚未
确认真实网关是否配置该地址、是否允许 ICMP，或是否提供替代 TCP/UDP 探测服务。因此目前
没有“经过真实网关确认”的连通性目标，不能把 ping 成功写成已完成判据。

恢复网关后应确定一种探测：优先使用网关虚拟 IPv4 的 ICMP；若禁用 ICMP，则指定仅通过
隧道监听的测试服务端口。成功必须同时满足 Tinc 日志/状态显示已连接到 `server_master`，
以及约定目标连续探测成功；仅有 tincd 进程运行不算隧道连通。

## 4. `/api/tinc/client/status/update`

新客户端当前不得启用此接口。真实代码和无副作用请求确认它仍是匿名遗留实现：

- `ids` 是 `tinc_node.id` 数据库主键；登录响应不返回该字段，客户端没有可靠来源；
- 只处理 `type = "add"` 且 `ids` 非空的情况；
- `result = "success"` 写入“配置成功/在线”，其他任意 result 写入“配置失败/离线”；
- 不支持待审批状态；
- 不校验 sid 或 Token，也没有 Token 失效响应；
- 缺少 ids、节点不存在或 type 不受支持时仍返回 HTTP 200、JSON `{"status":"success"}`；
- 异常也保持 HTTP 200，仅把 JSON status 改成 `error`。

因此它目前没有可供新客户端依赖的成功、失败、待审批或 Token 失效契约。若未来启用，后端
必须先改为 sid + Token 鉴权并从绑定节点派生数据库 ID，不能继续让客户端提交裸 `ids`；
同时需定义真实 HTTP 状态和稳定业务码后再联调。

## 5. 2026-09-09 实际验证记录

后端执行 `mvn -pl ruoyi-admin -am package -DskipTests`，5 个 Reactor 模块全部构建成功；
重新打包版本已启动在本机 8081。测试使用同一隔离网络中的两个节点，凭据和 Token 仅在测试
进程内存中存在，响应正文未保存。

| 验证项 | 结果 |
| --- | --- |
| 两个隔离节点登录 | 成功，登录 status 均为 1 |
| 非 JSON Content-Type | HTTP 415 / `CLIENT_CONTENT_TYPE_INVALID` |
| 非法 JSON | HTTP 400 / `CLIENT_REQUEST_INVALID` |
| 缺 Token | HTTP 401 / `CLIENT_TOKEN_INVALID` |
| 跨节点 Token | HTTP 401 / `CLIENT_TOKEN_INVALID` |
| 删除 Redis Token 索引后重试 | HTTP 401 / `CLIENT_TOKEN_INVALID` |
| 非法 action | HTTP 400 / `CLIENT_ACTION_INVALID` |
| 空 sid | HTTP 400 / `CLIENT_SID_INVALID` |
| 不存在节点 | HTTP 404 / `CLIENT_NODE_NOT_FOUND` |
| 非法公钥 | HTTP 400 / `CLIENT_PUBLIC_KEY_INVALID` |
| 旧实现：上传与本地副本相同的公钥 | HTTP 200；此结果已被成功回执机制取代，不再作为当前幂等证据 |
| 上传变化的合法测试公钥、网关不可用 | HTTP 502 / `GATEWAY_KEY_APPLY_FAILED` |
| 网关失败后的本地旧 host | SHA-256 不变 |
| status/update 无 ids、无有效鉴权 | HTTP 200 / `{"status":"success"}`，证明不可启用 |

`GATEWAY_HOST_UNAVAILABLE`、`GATEWAY_UNAVAILABLE`、`CLIENT_NETWORK_NOT_FOUND` 和
`CLIENT_SESSION_UNAVAILABLE` 本轮通过真实代码路径与构建确认，没有为制造错误而破坏本地
网关文件、修改数据库关联或停止 Redis，因此不标记为运行时故障注入已验证。

## 6. 仍阻塞事项

- 当前测试网关 TCP/22 不可达；
- 三个旧网关均未真实迁移到 router；
- 网关实际 OS、`tincd --version`、监听端口和运行方式未知；
- 网关虚拟 IPv4 与 ICMP/替代探测目标尚未在远程确认；
- 尚未完成第一次真实远程公钥写入、重载成功、第二次远程幂等确认和 Tinc 隧道握手；
- TAP/TUN 停止后的确定性空闲状态尚未在客户端实机验收。

## 7. 2026-09-09 后续复查

- 再次从本机探测选定隔离网关，SSH/TCP 22 仍不可达，未执行任何远程迁移或写入；
- 已确认占用开发 IPC 的 `target/debug/vpn-agent.exe` 由当前用户通过项目本地 Cargo 启动；
  核对来源后停止该进程，原 Cargo 父进程随后退出，没有操作其他高权限进程；
- 在客户端项目运行 `npm.cmd run test:e2e`，11 项本地集成检查全部通过；测试覆盖鉴权拒绝、
  key/upload Token 字段、上传失败、密钥复用、危险 ZIP 拒绝、并发配置、会话失效和日志脱敏；
- 本次 E2E 使用 fixture 后端，结果明确标记 `remoteBackend=fixture`、`realVpn=false`，不能作为
  真实网关迁移、Tinc 握手或隧道连通证据；测试结束后 `vpn-agent` 进程数为 0；
- 本机后端重新构建版本仍运行于 8081。

## 8. 2026-09-09 网关网络层复查

- 选定隔离网关的本地配置仍为 `Mode = switch`，本轮未修改本地或远程网关配置；
- 后端默认 SSH 密钥路径已配置，密钥文件存在，并且当前后端运行用户可读；检查仅验证文件元数据与可读性，未读取、输出或记录密钥正文；
- 从后端所在主机对该隔离网关执行脱敏探测：ICMP 不可达、SSH/TCP 22 不可达、配置声明的 Tinc/TCP 655 端口不可达；
- 因 TCP 连接尚未建立，当前失败发生在 SSH 认证之前，不能归因于用户名或密钥认证；管理员应优先检查测试网关电源/实例状态、路由、安全组、防火墙及 SSH/Tinc 服务监听；
- 在 TCP/22 恢复前，不执行 `Mode = router` 迁移、远程版本查询、公钥写入或 HUP，避免把未落地操作记录成成功；
- 本机后端 TCP/8081 复查正常；当前机器未发现可用于 Windows 实机链路验收的 `tincd.exe` 或 TAP/TUN 网卡，因此真实握手、连通探测和停止清理仍待隔离 Windows 实机完成。

## 9. 2026-09-09 后端契约加固与回归

结合客户端的严格标识、失败重试和“不伪造成功”策略，后端完成以下加固：

- 登录改为 `sid + password` 在精确 sid 记录中唯一定位；配置下载和公钥上传改为以 Token
  绑定的节点数据库 ID 在精确 sid 记录中定位。真实隔离库存在同 sid 的多条历史/网络记录，
  因此不能假设 sid 在数据库中全局唯一，也不能继续使用模糊查询第一条；
- sid 与网络名称使用和客户端一致的 1–64 位字母、数字、下划线规则，并拒绝 Windows
  保留名称，阻止 ZIP 路径、后台本地路径和远程命令参数越界；
- 配置下载先在内存中完整生成 ZIP，成功后再一次性写响应，避免生成中途产生半截 ZIP；
  发送前清除全局字符编码状态，保持精确 `Content-Type: application/zip`；
- 登录响应增加 `Cache-Control: no-store`；公钥上传请求体限制为 64 KiB；RSA 校验增加模数
  正/奇性及公钥指数检查；异常日志不记录底层异常消息；
- 公钥幂等增加成功应用回执，并在同一 JVM 内按网络/节点串行化。网关失败时旧 host 不变且
  不生成回执，避免仅因历史本地文件相同就在网关不可达时返回假 200。

重新构建并启动本机 8081 后，使用隔离节点进行脱敏真实接口回归：

| 验证项 | 结果 |
| --- | --- |
| 合法隔离节点登录 | status=1，响应带 `Cache-Control: no-store` |
| 只匹配真实 sid 的模糊前缀登录 | 拒绝 |
| 配置下载 | HTTP 200，精确 `application/zip` |
| ZIP 结构 | 仍仅 `<netName>/tinc.conf`、`<netName>/hosts/server_master` |
| 非法 sid 下载 | HTTP 400 / `CLIENT_SID_INVALID` |
| 超过 64 KiB 的公钥上传请求 | HTTP 413 / `CLIENT_REQUEST_TOO_LARGE` |
| 无成功回执且网关不可达时上传合法测试公钥 | HTTP 502 / `GATEWAY_KEY_APPLY_FAILED` |
| 上述失败后的本地 host | SHA-256 不变 |
| 上述失败后的成功回执 | 未创建 |

本轮没有修改客户端项目、下载 ZIP 白名单、真实网关或数据库业务数据。第一次远程成功后
生成回执、第二次相同公钥跳过 SFTP/HUP 的真实证据，仍必须等待隔离网关 TCP/22 恢复。

## 10. 2026-09-10 续接复查

- 按交接要求重新执行 `mvn.cmd -pl ruoyi-admin -am package -DskipTests`，5 个 Reactor
  模块全部构建成功；
- 续接时本机 8081 和 Redis/6379 均未监听。首次启动后端因本机 Redis 未运行而退出；随后仅
  启动当前用户可控的本地 Redis 进程，并关闭持久化、把工作目录限制在后端仓库日志目录，
  再启动本次构建的 `ruoyi-admin.jar`；HTTP 健康请求返回 200；
- 对脱敏标识 `gateway-59f2b5cbf5a7` 对应的选定隔离网关重新探测：ICMP 已可达，但
  SSH/TCP 22 和本地配置声明的 Tinc/TCP 端口仍不可达；本地副本仍为 `Mode = switch`；
- 在新启动的后端上完成无凭据、无业务写入的边界回归：非 JSON 上传返回 HTTP 415 /
  `CLIENT_CONTENT_TYPE_INVALID`，非法 JSON 返回 HTTP 400 / `CLIENT_REQUEST_INVALID`，
  非法 sid 下载返回 HTTP 400 / `CLIENT_SID_INVALID`，超过 64 KiB 的上传返回 HTTP 413 /
  `CLIENT_REQUEST_TOO_LARGE`；
- ICMP 恢复只证明实例或基础路由部分恢复，不能证明 SSH、SFTP、Tinc 服务或隧道可用。
  因 TCP/22 仍未建立，本轮未尝试 SSH 认证，未读取远程 OS/Tinc 版本，未修改远程配置，
  也未执行公钥写入或 HUP；
- 当前主阻塞因此收敛为管理员继续恢复隔离网关 SSH/Tinc 服务监听或对应防火墙/安全组。
  第一次真实远程公钥应用、第二次及并发幂等、`switch` 到 `router` 迁移和隧道握手仍阻塞。

本轮没有修改客户端项目、真实网关、数据库业务数据或本地网关配置，也没有把 ICMP 可达
误记为 Tinc 连通成功。

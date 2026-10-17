# TincLink Phase 1A 客户端路由与会话 API 契约

本文件描述当前 Java 服务端源码的接口事实。`allowed_routes` 保持已有 `string[]` 形态，以兼容当前 Rust 客户端；示例中的 `scope` 对象数组**没有**替换现有字段。配置 ZIP 的两项安全白名单未变。

Phase 1C-S 已以 Spring MVC HTTP/JSON 测试及隔离 Redis 集成测试核对本文字段和语义；实际脱敏响应与验证边界见 [Phase 1C-S 集成报告](TINCLINK_PHASE1C_SERVER_INTEGRATION_REPORT.md)。未在生产环境部署或验证。

## 1. 登录

`POST /api/tinc/client/login`，`Content-Type: application/json`。

请求体：`{"accessCode":"TL-XXXX-XXXX","password":"..."}`。`access_code` 是 `accessCode` 的兼容别名；仅旧客户端可用 `sid` 代替接入码，且节点名全局不唯一时拒绝。不能通过请求指定 `networkId`、对端 IP 或路由。

成功为 HTTP 200、JSON：

```json
{
  "status": 1,
  "sid": "client_a",
  "access_code": "TL-XXXX-XXXX",
  "token": "<opaque-token>",
  "net_name": "new_network",
  "network_id": 8,
  "node_id": 5,
  "node_ip": "10.0.11.211",
  "msg": "登录成功",
  "allowed_routes": ["10.0.11.1/32", "10.0.11.212/32"],
  "route_revision": "routes-<64位小写SHA-256十六进制>",
  "route_lease_seconds": 120
}
```

`status` 为旧协议的数字；`token` 是不透明凭据，服务端只在 Redis 保存其 SHA-256 索引，不应写入日志。旧成功字段均保留。

## 2. Keepalive

`POST /api/tinc/client/keepalive`，`Content-Type: application/json`。

请求体：`{"sid":"client_a","token":"<opaque-token>"}`。成功为 HTTP 200：

```json
{
  "status": "success",
  "msg": "心跳成功",
  "allowed_routes": ["10.0.11.1/32", "10.0.11.212/32"],
  "route_revision": "routes-<64位小写SHA-256十六进制>",
  "route_lease_seconds": 120
}
```

Keepalive 不轮换 `token`，只在旧 Token 尚有效、节点仍获授权、Redis 原子校验成功时延长其 1800 秒 TTL。已过期或已撤销 Token 不会复活。

## 3. 路由快照

`allowed_routes` 必填、类型 `string[]`；每项为规范 IPv4 `/32` CIDR。数组首项为当前 Network 网段的网关 `.1/32`（隐含 `gateway` 范围），其余为同一 `network_id` 下符合接入资格的其他节点（隐含 `peer` 范围），按字符串升序、去重；不含自身、跨网节点、已撤销/禁用节点或非法地址。离线但仍获授权的节点继续存在。最大 1024 项，含网关。该数组是客户端路由期望快照，**不是** Tinc 数据面的 ACL 或已建立直连的断开证明。

`route_revision` 必填、类型 `string`，计算为 `routes-SHA256(UTF-8规范内容)`；规范内容依次是 `gateway=<CIDR>\n`、每条排序后的 `peer=<CIDR>\n`。同一授权 CIDR 集合及网关/对端角色产生相同版本；路由增减或网关变化会改变版本；Keepalive、节点在线/离线、数组查询顺序不会改变版本。版本和路由来自同一成员查询的内存快照。`route_revision` 不代表数据面已握手。

`route_lease_seconds` 必填、正整数秒，当前为 `120`。成功响应到达客户端时获得新的租约；下一次成功 Keepalive 续租。服务端 Token TTL 为 1800 秒且成功 Keepalive 在返回前续期，因此租约短于 Token TTL。HTTP 错误、超时或缺少成功响应都**不签发**新租约。短时 503/网络异常可以在当前租约内重试；租约过期后不得把旧快照视为仍获授权。401/410 是会话失效/撤销，与可重试的 503 不同。客户端具体路由操作由客户端项目自行决定。

## 4. 错误响应

失败时不返回 `allowed_routes`、`route_revision` 或 `route_lease_seconds`。登录保留旧 `status:0` + `code` + `msg` 包装：错误凭据、非法 SID、旧 SID 歧义、无效节点地址或网络缺失当前可能是 HTTP 200；限流为 429 `CLIENT_LOGIN_RATE_LIMITED`；已禁用/撤销节点为 410 `CLIENT_NODE_REVOKED`；路由查询失败为 503 `CLIENT_ROUTE_SNAPSHOT_UNAVAILABLE`；Redis/会话不可用为 503 `CLIENT_SESSION_UNAVAILABLE` 或 `CLIENT_LOGIN_LIMITER_UNAVAILABLE`。

Keepalive 错误体为 `{"status":"error","code":"...","msg":"..."}`：400 `CLIENT_SID_INVALID`，401 `CLIENT_TOKEN_INVALID`，410 `CLIENT_NODE_REVOKED`，503 `CLIENT_ROUTE_SNAPSHOT_UNAVAILABLE` 或 `CLIENT_KEEPALIVE_FAILED`。物理删除后节点不存在时，旧 Token 也不能访问 API；具体错误可能是 404 `CLIENT_NODE_NOT_FOUND` 或 401，而不保证始终为 410。客户端须同时判断 HTTP 状态和 `status/code`。

## 5. 其他接口的兼容边界

`POST /api/tinc/client/config/download` 仍只下发 `<netName>/tinc.conf` 和 `<netName>/hosts/server_master`。`POST /api/tinc/client/key/upload` 的成功仍为 `text/plain` 网关 Host 内容。`POST /api/tinc/client/status/update` 请求格式未变。上述接口继续验证原有节点 Token，并在撤销/禁用时拒绝；未新增客户端必须提供的 OS 字段、脚本或路由请求参数。

只读核对客户端 Phase 1B 源码后，Rust Agent 已在 `crates/vpn-core/src/api.rs` 解析 `allowed_routes`、`route_revision`、`route_lease_seconds`，并在 `crates/vpn-core/src/engine.rs` 使用路由版本与租约截止时间。服务端本阶段未修改或构建客户端；跨项目端到端互操作仍须在隔离环境验证。

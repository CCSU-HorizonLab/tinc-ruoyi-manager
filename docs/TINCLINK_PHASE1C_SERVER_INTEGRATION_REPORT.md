# TincLink Phase 1C-S 服务端集成验证报告

日期：2026-10-17。仅涉及服务端仓库、本机临时 Redis 和本机隔离 WSL；未连接生产 MySQL、Redis、网关或 Tinc Runtime，未修改客户端、未部署、未推送。

## 范围与验证层次

- `TincClientApiHttpContractTest` 通过 Spring MVC `MockMvc` 对真实 `TincClientApiController` 发起 HTTP 风格请求，由 Spring 完成路由、状态码与 JSON 序列化；节点/网络使用隔离的内存测试数据及模拟服务。它不是监听真实 TCP 端口的完整 Spring Boot 部署测试。
- `TincClientSessionStoreRedisIntegrationTest` 使用实际隔离 Redis 的 `redis-cli --eval` 执行生产 `TincClientSessionStore` 所持有的同一份 Lua 脚本。CLI 执行器仅替换 Redis 传输层，不能单独证明 `RedisTemplate/Lettuce` 的序列化和调用。
- `TincClientSessionStoreLettuceIntegrationTest` 在本机隔离 WSL 中对 `127.0.0.1:16379` 的无持久化 Redis 执行实际生产 `RedisConfig`、`RedisCache`、`RedisTemplate/Lettuce` 和 `TincClientSessionStore` 调用链。Windows Redis 与 WSL Redis 均仅绑定各自回环地址，测试后停止。
- `TincClientRouteSnapshotTest` 在成员集合并发替换下验证路由和 Revision 始终来自同一份查询结果。

## HTTP 契约实测样例

下列来自 Spring MVC 测试响应，Token 已替换为占位符；其他 ID/IP 均为隔离夹具，非生产资产。响应字段次序不属于契约。

`POST /api/tinc/client/login` → HTTP 200：

```json
{
  "status": 1,
  "sid": "client_a",
  "access_code": "TL-7K9M-2Q4F",
  "token": "<redacted>",
  "net_name": "new_network",
  "network_id": 8,
  "node_id": 5,
  "msg": "登录成功",
  "node_ip": "10.0.11.211",
  "allowed_routes": ["10.0.11.1/32", "10.0.11.212/32"],
  "route_revision": "routes-fb1146958405978f5575add76cf5873d4eec7aeca3ad98ac7bc42657a92f085a",
  "route_lease_seconds": 120
}
```

`POST /api/tinc/client/keepalive` → HTTP 200：

```json
{
  "status": "success",
  "msg": "心跳成功",
  "allowed_routes": ["10.0.11.1/32", "10.0.11.212/32"],
  "route_revision": "routes-fb1146958405978f5575add76cf5873d4eec7aeca3ad98ac7bc42657a92f085a",
  "route_lease_seconds": 120
}
```

测试按 UTF-8 响应字节解析 JSON，逐项断言旧字段、`allowed_routes` 每项是字符串、网关首项和路由 Revision 的 SHA-256 规范文本一致。节点 B 置为已撤销后，后续 Keepalive 只含网关且 Revision 变化；从成员表移除 B 后 Revision 与该快照保持一致。无效 Token 返回 HTTP 401 `CLIENT_TOKEN_INVALID`，撤销 Token 返回 HTTP 410 `CLIENT_NODE_REVOKED`，Redis 异常返回 HTTP 503 `CLIENT_KEEPALIVE_FAILED`，登录路由查询异常返回 HTTP 503 `CLIENT_ROUTE_SNAPSHOT_UNAVAILABLE`；这些错误均不包含新路由租约或 Token。

## Redis、租约和撤销

隔离 Redis 的真实 Lua 测试覆盖：签发、认证、跨节点拒绝、续期、索引与 Token TTL、真实过期（1 秒 TTL 后再检查）、永久撤销、24 小时墓碑、凭据轮换代际、临时签发阻断、并发续期与撤销、不可达 Redis 故障关闭。`RedisTemplate/Lettuce` 测试覆盖同一生产调用链、旧版 FastJson JSON 字符串 Token/索引格式兼容、轮换后旧会话失效以及连接失败关闭。成功续期后实测 Token/索引 TTL 大于 120 秒路由租约，且不超过 1800 秒。

DB/Redis/网关不是跨系统原子事务。节点删除和禁用走 `TincNodeMangeServiceImpl.revokeRuntimeNode`：先让 Redis 会话失效，再调用网关 `deletePeer`，必须收到 `OK` 且网络 `READY` 才允许数据库删除/方法成功；Router 抛错或未 READY 时 API 不应宣称撤销成功，数据库事务回滚，Redis 保持失败关闭。公钥上传和撤销在单 Management 进程内使用相同节点锁，并在写 Host 前重新读取节点和 Token；SQL 条件更新阻止迟到状态写入覆盖撤销。测试覆盖 Router 重载失败时 DB 删除入口未执行、已撤销节点不能以旧状态回退误报公钥或心跳成功，以及新路由快照排除已撤销成员。

仍有边界：若网关 Host 已删除但 HUP 失败，或 DB 最终提交失败，三者可能短时不一致，需查看 Runtime 状态并由管理员幂等重试；现无跨系统补偿事务。单进程锁不支持多 Management 实例。网关撤销不等于已建立的 Tinc P2P 直连立刻断开，**数据面断连未验证**。

## 测试和交付状态

- Windows 完整 Maven 套件启用临时 Redis CLI 集成：146 项、0 失败、0 错误、5 跳过；4 项 CLI Redis 集成测试实际运行并通过。5 个跳过项包含 4 项只在 WSL 启用的 Lettuce 测试和 1 项既有跳过项。
- WSL 隔离 Redis 的 Lettuce 定向测试：4 项、0 失败、0 错误、0 跳过；包含旧版 Redis 会话格式兼容。
- `mvn -pl ruoyi-admin -am -DskipTests package` 成功，本地 JAR 可构建；`git diff --check` 无补丁空白错误。
- 交付契约文件为 [TINCLINK_PHASE1A_CLIENT_ROUTE_API_CONTRACT.md](TINCLINK_PHASE1A_CLIENT_ROUTE_API_CONTRACT.md)，本阶段未变更其字段类型、路由顺序、Revision 或租约语义。
- 满足进入 **Phase 2 隔离环境部署测试** 的代码/测试前置条件；不代表生产可用。下一阶段仍需在隔离 Management + DB + Agent + Tinc 网关端到端验证 Host/HUP、真实客户端接入、撤销后直连行为和失败恢复。

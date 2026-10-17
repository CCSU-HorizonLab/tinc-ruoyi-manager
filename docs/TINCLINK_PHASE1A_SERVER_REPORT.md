# TincLink Phase 1A 服务端实施报告

后续隔离 Redis、Spring MVC HTTP 契约及并发验证已记录在 [Phase 1C-S 集成报告](TINCLINK_PHASE1C_SERVER_INTEGRATION_REPORT.md)。本文件保留 Phase 1A 当时的验证状态，不应作为最新集成结论。

日期：2026-10-17。范围仅为当前服务端仓库；客户端仓库仅只读核对 API 类型。未修改生产数据库、网关、线上 Redis，未部署、未推送。

## 实现与代码位置

| 文件 | 本阶段职责 |
|---|---|
| `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/TincClientApiController.java` | `login`、`keepAlive` 返回路由快照；`uploadKey`、`editAddInfo` 校验 Token/节点状态，公钥上传与撤销按节点串行，失败关闭 |
| `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/TincClientRouteSnapshot.java` | `build` 从稳定 Network ID 查询成员，过滤撤销/禁用/跨网/非法 IP，含网关 `/32`，产生同一内存快照的内容哈希 Revision |
| `ruoyi-system/src/main/java/com/ruoyi/tinc/session/TincClientSessionStore.java` | `issue`、`isValid`、`renew`、`revokeNode` 用 Redis Lua 原子签发、认证、续期、代际撤销与墓碑；Token 保持 30 分钟滑动 TTL |
| `ruoyi-system/src/main/java/com/ruoyi/tinc/session/TincClientLifecycleLocks.java` | 单 Management 进程内按节点串行公钥上传与网关 Host 撤销 |
| `ruoyi-system/src/main/java/com/ruoyi/tinc_node/service/impl/TincNodeMangeServiceImpl.java` | `revokeRuntimeNode`、`deleteTincNodeMangeById(s)` 删除/禁用时先使会话失效，再删除网关 Host、确认 Runtime READY，最后才允许 DB 删除；`rotateAccessCode` 和密码变更撤销旧 Token |
| `ruoyi-system/src/main/resources/mapper/tinc_node/TincNodeMangeMapper.xml` 及接口 | 客户端状态的限列条件更新；禁止迟到心跳或公钥回执覆盖撤销/禁用状态 |

现有客户端 Rust `api.rs` 只读检查表明 `allowed_routes` 必须为字符串 `/32` 数组，且 Engine 会自行去重网关。因此没有把该字段改为对象数组，也未改 ZIP、安全白名单或客户端源码。

## 路由、Revision 与租约

登录和 Keepalive 均返回 `allowed_routes: string[]`、`route_revision: string`、`route_lease_seconds: 120`。网关由服务端 Network 网段前三段加 `.1/32` 得出；其他成员经同一 `network_id` 查询后筛选。网关始终第一项，对端按字符串排序，去重，最多 1024 项。`node_status=离线` 不撤销成员资格；明确 `禁用`、`已撤销`、`disabled`、`revoked` 状态被排除。当前表没有独立的“成员准入状态”列，故暂用明确拒绝状态及物理删除；不能把 `配置失败` 或暂时离线等同撤销。

Revision 是规范文本的 SHA-256：先 `gateway=<CIDR>\n`，再按序 `peer=<CIDR>\n`；不含当前时间、在线状态或数据库查询顺序。路由和 Revision 由同一次成员查询构建，避免分别读取造成自身不一致。不同时间的登录与 Keepalive 仍可能跨越管理员变更，不宣称跨 MySQL/Redis/Runtime 的原子快照。

Token TTL 为 1800 秒；成功 Keepalive 在返回前通过一个 Redis Lua 脚本同时确认 Token 未过期、节点代际未变、索引成员仍在、撤销墓碑不存在，再原子更新 Token 和索引 TTL。任何 Redis 错误不返回成功租约。120 秒路由租约从客户端收到成功响应起算；HTTP 失败或超时不续租。客户端应区分 401/410 的授权失效与 503 的短时服务不可用，并在租约到期后不再信任旧快照。当前 Rust Agent 尚未消费新 Revision/Lease 字段，需客户端后续实现；本阶段未越界改动。

## 撤销链路与安全边界

Redis 撤销脚本先递增节点会话代际，再为索引内旧 Token 写 24 小时墓碑、删除 Token 和索引，且阻止发新 Token。删除节点时签发阻断不设自动过期；轮换接入码/密码或临时禁用时阻断 60 秒，允许 DB 事务完成。即使旧索引意外丢失，代际变更也使旧 Token 续期失败。物理删除和禁用还经 Runtime Router 删除网关 Host 并确认 READY；若 Runtime 失败，数据库删除/禁用事务回滚且返回错误，**不会声称撤销完成**。会话可能已经先被拒绝，这是刻意的失败关闭。

同进程节点锁避免已撤销节点在 Host 删除后由迟到的公钥上传重新写回；上传在锁内重读节点与 Redis 会话，删除在同锁内执行 Redis 撤销及 Runtime 更新。管理端只有单实例时这一边界有效；未来多 Management 需分布式协调。DB、Redis、网关不存在跨系统事务：例如网关撤销成功而 DB 提交失败时需要人工或幂等重试修复，不能宣称绝对一致。Tinc 客户端之间可直连，网关 Host 撤销和 HUP 不等于已建立直连瞬间全部断开；该数据面性质没有在生产或测试网关实测。

## 自动化验证

运行：`mvn -pl ruoyi-admin -am '-DargLine=-Djava.io.tmpdir=D:\Codes\Java\KenDeJi_RuoYi\RuoYi-Vue-master\ruoyi-common\target' test`。最后一次完整运行成功，共 132 项测试、0 失败、0 错误、1 跳过（原有跳过项）。新增/扩展测试覆盖：A/B 对端与网关 `/32`、当前节点与跨网过滤、离线保留、撤销与非法地址过滤、Revision 稳定/变化、登录兼容字段、过期与撤销拒绝、Redis 异常失败关闭、旧 Redis JSON 字符串格式及脚本参数类型、公钥上传与撤销交错拒绝、迟到状态回退不误判禁用节点、Runtime 撤销失败不删除 DB、客户端状态 SQL 不覆盖禁用。`mvn -pl ruoyi-admin -am -DskipTests package` 成功，生成本地 `ruoyi-admin/target/ruoyi-admin.jar`；`git diff --check` 无补丁空白错误。

隔离 Redis 集成测试代码位于 `TincClientSessionStoreRedisIntegrationTest`，默认不连接任何 Redis；需显式传 `-Dtinclink.test.redis.port=16379`。本轮尝试启动了只绑定 `127.0.0.1:16379` 的临时 Redis（`PING=PONG`），但当前 Windows 执行环境禁止 Java/Netty 创建 loopback selector，测试在建立连接前失败，故**真实 Redis Lua 集成未验证**，不计入通过项；临时进程已确认关闭。上线前必须在可用的隔离环境跑通该项，不得以单元测试代替。

首次完整测试曾因 Windows 沙箱默认临时目录的原子文件写入权限而失败；把测试 JVM 的 `java.io.tmpdir` 指向本仓库 `ruoyi-common/target` 后完整套件通过。该失败不是 Tinc 或 Redis 逻辑失败。

## Git、部署与未完成项

本轮未提交、未推送、未部署。工作树原先已有用户修改的 `RuoYiApplication.java` 和多份未跟踪文档/SQL；均未编辑或删除。Phase 1A 新增/修改文件以本报告“实现与代码位置”和上述测试文件为准。无需数据库 migration；上线前仍需在隔离环境完成 Redis Lua 集成、管理员撤销与真实 Tinc Host/HUP 联调，再按正常变更流程备份与部署。不可把本地编译成功称作生产可用。

当前未解决：客户端尚不消费 Lease/Revision；单 Management 进程锁不提供多实例一致性；没有独立成员准入列；跨 DB/Redis/网关不能原子提交；已建立的 Tinc 直连撤销效果未实测。后续客户端交接仅以 [API 契约](TINCLINK_PHASE1A_CLIENT_ROUTE_API_CONTRACT.md) 为准。

# 监控数据语义与验证

本次修复不新增采集服务，不更改数据库，不启动或重启 Tinc。

## 已实现

- `/monitor/network/global-stats` 根据 Network.serverId 路由至对应 Local/Agent Runtime，仅调用 `inspectNetwork`。
- `/monitor/network/single-stats/{networkId}` 使用稳定网络 ID；Server 下拉框也改为提交 serverId。
- `READY` 表示网关运行时报告就绪，不表示 Windows/Linux 客户端完成握手或 Ping。
- Runtime 返回的 `NOT_READY` / `DEGRADED` 是真实观测；查询异常为 `UNKNOWN + STATUS_QUERY_FAILED`，不回退到模拟正常状态。
- 全局比例的分母为全部登记网络，分子为本次确认 READY 的网络；UNKNOWN 不算就绪，空集合返回 null。
- Agent ONLINE 计数只表示数据库里的上次探测结果，不是实时连通证据；LOCAL 不冒充 ONLINE Agent。
- 节点数量与“已配置”数量来自数据库；历史 nodeStatus 没有可靠失效时间，不用它推算当前在线率。
- 前端失败后清空旧结果，并显示查询失败；重复刷新不并发叠加，切换网络后的旧响应不会污染新目标。

## 2026-10-22 新增的真实观测

- Local Tinc Runtime 从 Linux 虚拟网卡的 sysfs `rx_bytes` / `tx_bytes` 读取累计计数，并附上采样时间。只有接口存在且两个计数均有效时才返回；Access Agent 暂未提供对应计数，仍显示“未采集”。
- 单网络页面在同一网络、同一接口的两次有效采样后按计数差和实际采样间隔显示接收、发送速率；采样间隔须在 1–120 秒之间。接口切换、计数回退、长时间挂起或查询失败时不沿用旧速率，等待下一次有效采样。网卡计数可能包含非 Tinc 业务流量，不等同于公网链路吞吐量。
- 每次 Runtime 状态探测耗时由服务端实际计时，只表示管理端查询耗时，不代表客户端到网关的 VPN 往返延迟。
- 页面同时保留真实 Runtime 就绪状态和资产数量。不能把 READY 推断为节点握手、端到端连通或服务质量。

## 未采集

VPN 端到端延迟、公网吞吐量、健康评分、平均恢复时间、七天趋势、节点实时在线率尚无可靠采集源。
接口保留旧字段时返回 null 或空数组，页面显示“未采集”，不再返回随机数或绘制固定样本曲线。

## 自动化验证

- `NetworkMonitorControllerTest`：Local / Access-A / Access-B 路由、READY 统计、零就绪、查询异常脱敏、缺失关联和节点配置状态语义。
- `ruoyi-ui/tests/monitor-behavior.test.cjs`：直接执行 Vue 组件方法，检查失败状态、零值保留、刷新并发、稳定 ID 与过期响应隔离。
- `npm test`；`npm run build:prod`。
- `mvn -pl ruoyi-admin -am "-Dtest=NetworkMonitorControllerTest,TincClientApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`。

这些测试使用受控 Runtime/API 替身，不代表生产环境或真实客户端端到端验收。
完整全局状态探测目前串行执行；网络较多或 Agent 超时时可能较慢，尚未进行容量压测。

2026-10-22 本地验证：`InterfaceTrafficReaderTest` 2 项、`NetworkMonitorControllerTest` 6 项通过；前端 `npm test` 23 项通过；Management 与 Access Agent 打包成功，Vue 生产构建成功。上述测试未使用生产流量，不构成生产监控验收。
第一次完整 `mvn -pl tinclink-access-agent -am test` 在 Windows 受限默认临时目录中的既有原子写入测试出现 `AccessDeniedException`。将本次测试的 `TMP` / `TEMP` 指向工作区内临时目录后重跑通过：ruoyi-common 27 项（26 通过、1 跳过），Access Agent 4 项通过。这个修正仅改变本地测试临时目录，没有修改业务代码或生产系统。
相同临时目录设置下完整 `mvn -pl ruoyi-admin -am test` 通过：ruoyi-common 27 项（1 跳过）、ruoyi-system 55 项、ruoyi-admin 63 项（4 跳过）；Redis 集成测试未在本机执行，不能算作真实 Redis 验证。
2026-10-24 追加验证：服务端监控定向测试 8 项通过；前端 `npm test` 24 项通过，新增“长间隔与计数器回退不产生当前速率”用例；Vue 生产构建成功，仍有原有资源体积和 Browserslist 提示。今日的前端采样间隔修正尚未部署到服务器。

2026-10-11 本地验证：35 项相关后端测试通过，Management Maven package 成功；17 项前端测试通过。
默认 dist 输出目录遇到 EPERM 权限错误，因此使用以下独立输出目录完成生产构建：
`node node_modules/@vue/cli-service/bin/vue-cli-service.js build --dest target/monitor-ui-validation`。
构建成功，保留既有资源体积及 Browserslist 数据过期警告；未升级依赖。

## 部署及回滚

本次提交包含前后端契约调整，部署时应一并更新 Management JAR 与 Vue 静态文件，并保留旧版本备份。
Git 推送本身不会更新生产服务器。本轮未执行生产部署、数据库变更或 Tinc 重启。
上线前在测试环境覆盖：正常、全部未就绪、Agent 不可达、HTTP 查询失败、跨 Server 切换网络。
如需回滚，应回退同一提交的前后端产物，避免新旧监控契约混用。

2026-10-22 生产预检发现：线上 8081 由手工 Java 进程提供服务，`ruoyi-tinc-backend.service` 为 inactive；生产数据库缺少新 JAR 启动时必需的 `tinc_resource_grant` 表。经用户单独授权后完成以下部署：

1. 在服务器 `/root/tinclink-maintenance-backup/20261022-monitoring-real-data` 备份生产 `ry-tinc` 数据库、旧 JAR、原页面目录及启动配置；备份目录仅 root 可读，数据库导出非空，旧 JAR SHA-256 为 `d092d349c40eb910af8239fca94482880d55a18fb91a8d55a41613d38a553d7e`。
2. 上传并核对 `sql/20261011_tinc_resource_grants.sql` 的 SHA-256，仅创建 `tinc_resource_grant` 表，验证 InnoDB、五个必需列、联合主键和资源索引；未修改其他业务表数据。
3. 新 JAR SHA-256 `5d0535d987316e2d4e8586cabfdfb29ea06891b6a80682d224c2eec251da6439`。受控终止原手工 Java 进程后替换 JAR，以现有 `ruoyi-tinc-backend.service` 启动；systemd active，MainPID `1901049`，8081 返回 200。没有并行开启第二个长期 JVM。
4. 将新版页面静态文件部署到原 Nginx 静态目录，保留旧目录 `/www/wwwroot/ry-vue-tinc.pre-20261022-monitoring`。83 返回 200，入口引用的 5 个 JS/CSS 资源均返回 200；未修改 Nginx 配置。
5. 部署前后 `new_network` Tinc PID 均为 `2183848`，`network1` PID 均为 `2203449`；600/655 的 TCP、UDP 继续监听。宿主 `tinc@1212` 在本次部署前已经 inactive，602 无监听；本轮未启动或修改该网络。

未登录的监控请求仍按若依包装返回业务码 401（HTTP 状态为 200），不公开监控数据。由于未使用管理员凭据，本轮**未通过生产认证会话读取监控 JSON 或验证浏览器中每个 Network 的具体数值**。服务器 `/sys/class/net/tinc0` 与 `/sys/class/net/tinc1` 的累计计数源确实可读；这不等于客户端 RTT 或端到端连通验收。需管理员登录页面手动刷新核对；若 Access Agent 网络没有接口计数，则应显示“未采集”。

2026-10-24 上线后复核：公网 `http://8.148.13.80:83/` 返回 HTTP 200；页面入口文件 SHA-256 与本地已部署构建一致（`fe10626c73a85847f08133607b2d18ad9d149bd4b86b9220cb6324e1f679c7ad`）。实际 API 代理 `/prod-api/monitor/network/global-stats` 返回 JSON 业务码 401（未认证，非公开数据），说明公网 Nginx 到后端链路仍在响应。SSH 22 本次连接在 banner 阶段超时，不能复核当前 PID、JAR 校验值及网卡计数；不能把 2026-10-22 的 PID 检查冒充为今日结果。当前桌面没有已打开的 Edge/Chrome 登录窗口，且登录验证码启用，因此仍未完成带权限的生产监控 JSON/UI 验收。未对生产服务器作任何修改。

回滚：停止 `ruoyi-tinc-backend.service`，用备份目录的旧 JAR 恢复应用 JAR，并恢复旧前端目录；若旧版需原手工启动方式，可按备份的启动配置启动，避免并发 Java。授权表为新增空表，回滚应用时可暂时保留，不必在紧急回滚时删除数据库对象。整个过程无需停止或重载 Tinc。

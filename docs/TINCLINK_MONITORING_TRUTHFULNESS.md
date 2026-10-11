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

## 未采集

延迟、流量、健康评分、平均恢复时间、七天趋势、节点实时在线率尚无可靠采集源。
接口保留旧字段时返回 null 或空数组，页面显示“未采集”，不再返回随机数或绘制固定样本曲线。

## 自动化验证

- `NetworkMonitorControllerTest`：Local / Access-A / Access-B 路由、READY 统计、零就绪、查询异常脱敏、缺失关联和节点配置状态语义。
- `ruoyi-ui/tests/monitor-behavior.test.cjs`：直接执行 Vue 组件方法，检查失败状态、零值保留、刷新并发、稳定 ID 与过期响应隔离。
- `npm test`；`npm run build:prod`。
- `mvn -pl ruoyi-admin -am "-Dtest=NetworkMonitorControllerTest,TincClientApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`。

这些测试使用受控 Runtime/API 替身，不代表生产环境或真实客户端端到端验收。
完整全局状态探测目前串行执行；网络较多或 Agent 超时时可能较慢，尚未进行容量压测。

2026-10-11 本地验证：35 项相关后端测试通过，Management Maven package 成功；17 项前端测试通过。
默认 dist 输出目录遇到 EPERM 权限错误，因此使用以下独立输出目录完成生产构建：
`node node_modules/@vue/cli-service/bin/vue-cli-service.js build --dest target/monitor-ui-validation`。
构建成功，保留既有资源体积及 Browserslist 数据过期警告；未升级依赖。

## 部署及回滚

本次提交包含前后端契约调整，部署时应一并更新 Management JAR 与 Vue 静态文件，并保留旧版本备份。
Git 推送本身不会更新生产服务器。本轮未执行生产部署、数据库变更或 Tinc 重启。
上线前在测试环境覆盖：正常、全部未就绪、Agent 不可达、HTTP 查询失败、跨 Server 切换网络。
如需回滚，应回退同一提交的前后端产物，避免新旧监控契约混用。

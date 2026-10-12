# TincLink 客户端登录限流契约

适用接口：`POST /api/tinc/client/login`，涵盖接入码模式和兼容期的 SID 模式。无需数据库迁移，也不修改配置 ZIP、公钥上传、客户端服务器地址或 Tinc Runtime。

服务端在查询节点和校验密码之前，通过 Redis 原子脚本扣减两个固定窗口预算：同一实际 TCP 对端每 60 秒最多 120 次；同一归一化接入码或 SID 每 300 秒最多 8 次。接入码大小写和 `accessCode`/`access_code` 两种请求字段共用预算；SID 使用独立预算。只信任请求连接的 `remoteAddr`，不信任调用者可伪造的 `X-Forwarded-For`。经同宿主反向代理进入的请求可能共享来源预算，部署时应监控 429 比例；调整阈值需要代码审查和回归测试。

Redis 键只包含来源或凭据标识的 SHA-256 摘要，不存密码、Token、接入码或原始 IP。成功认证会清除该凭据的预算，来源预算保留；失败和未知接入码消耗相同预算。窗口到期自动释放，不需要后台手工解锁。

| 情况 | HTTP | JSON `status` / `code` | 客户端处理 |
| --- | --- | --- | --- |
| 正常成功 | 200 | `1` / 原成功结构 | 继续获取配置 |
| 凭据错误 | 200 | `0` / `CLIENT_CREDENTIAL_INVALID` | 检查接入码或密码 |
| 尝试过于频繁 | 429 | `0` / `CLIENT_LOGIN_RATE_LIMITED` | 根据 `Retry-After` 响应头或 JSON `retry_after`（秒）等待，不立即重试 |
| 限流 Redis 不可用 | 503 | `0` / `CLIENT_LOGIN_LIMITER_UNAVAILABLE` | 稍后重试，不将其当作密码错误 |

旧 SID 同名歧义仍返回原有 `CLIENT_LEGACY_LOGIN_AMBIGUOUS`。限流失败不会签发 Token。登录日志只记录模式、内部节点 ID 和错误类型，不记录用户输入的 SID、接入码、密码或 Token。

上线前先确认 Redis 可用，并用一个隔离测试节点检查正确凭据成功、错误凭据、429、等待后恢复和 503 故障分支。推送代码并不表示已在生产服务器部署或验证这些 HTTP 场景。

# Tinc 客户端配置包响应说明

更新时间：2026-09-08。本文先根据后端源码完成静态分析，再使用本地测试节点完成
`/api/tinc/client/config/download` 的实际复测。记录不包含节点名、密码、Token、公钥
正文或网关地址。

## 下载实现位置

控制器与 ZIP 生成逻辑位于：

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/TincClientApiController.java`
  的 `downloadConfig()`，映射为 `POST /api/tinc/client/config/download`。
- 该方法通过 `ITincNodeMangeService` 查询节点、通过
  `ITincNetworkMangeService` 查询网络；没有单独的配置包 Service 或 ZIP 模板文件。
- `TincConfigUtils.readHostFile(netName, "server_master")` 从后台本地副本
  `D:/tinc/<netName>/hosts/server_master`（Windows）或
  `/etc/tinc/<netName>/hosts/server_master`（Linux）读取网关 host 文件。
- 网关 host、服务端 `tinc.conf`、脚本和私钥由
  `ruoyi-common/src/main/java/com/ruoyi/common/utils/TincConfigUtils.java` 生成；
  网络初始化入口为
  `ruoyi-system/src/main/java/com/ruoyi/tinc_network/service/impl/TincNetworkMangeServiceImpl.java`。

## 当前响应契约

成功响应的 `Content-Type` 是精确的 `application/zip`，并设置
`Content-Disposition: attachment; filename="config.zip"`。响应采用流式
`ZipOutputStream`，未设置 `Content-Length`，因此源码无法给出固定响应字节数。
大小取决于网络名称、节点名称以及网关 RSA 公钥内容。

下载接口现只写入以下两个文件，且不写显式目录 ZIP 条目：

| 路径 | 类型 | 解压后大小 | 来源 |
| --- | --- | --- | --- |
| `<netName>/tinc.conf` | 文件 | 随节点名变化；`client_fixture` 样本为 82 字节 | `downloadConfig()` 直接组装 |
| `<netName>/hosts/server_master` | 文件 | 随 Address、Port、Subnet 和 RSA 公钥变化 | 本地网关配置副本 |

如果 `hosts/server_master` 缺失或为空，接口现在会失败，且不会伪造默认公钥、把数据库密码当公钥或返回不完整 ZIP。客户端应显示此错误并由管理员修复网关网络配置。

## 旧响应的额外条目

本次修改前，接口每次固定生成下列 6 个文件，不写显式目录条目：

| 路径 | 类型 | 解压后大小 | 来源和用途 | 结论 |
| --- | --- | ---: | --- | --- |
| `<netName>/tinc.conf` | 文件 | 随节点名变化 | 控制器直接生成客户端主配置 | 保留，已改为兼容指令 |
| `<netName>/hosts/server_master` | 文件 | 随网关 host 内容变化 | 本地网关 host 副本 | 保留；连接 `server_master` 所必需 |
| `<netName>/tinc-up` | 文件 | 约 73 字节加节点 IP 长度差异 | 控制器直接生成 Linux `ifconfig` 启动脚本 | 停止下发；新客户端自行受控配置网卡 |
| `<netName>/tinc-down` | 文件 | 37 字节 | 控制器直接生成 Linux `ifconfig` 停止脚本 | 停止下发 |
| `<netName>/tinc-up.bat` | 文件 | 约 123 字节加节点 IP 长度差异 | 控制器直接生成 Windows `netsh` 启动脚本 | 停止下发；不可由 ZIP 信任执行 |
| `<netName>/tinc-down.bat` | 文件 | 86 字节 | 控制器直接生成 Windows `netsh` 停止脚本 | 停止下发 |

静态代码未发现该下载接口向 ZIP 写入以下项目：`rsa_key.priv` 或其他私钥、EXE、DLL、说明文件、旧 Qt 客户端程序、Token、密码或显式目录条目。网关自身确实使用 `rsa_key.priv`，但该文件只应留在网关，绝不能下发给客户端。

## 配置指令

下载后的 `tinc.conf` 使用以下全部指令：

| 指令 | 值 | 用途 |
| --- | --- | --- |
| `Name` | 已登录的 `sid` | 标识本客户端节点；必须和登录响应一致 |
| `Mode` | `router` | 三层路由模式；已由旧的 `switch` 统一改为 `router` |
| `AddressFamily` | `ipv4` | 限定 IPv4 连接 |
| `ConnectTo` | `server_master` | 指定首先连接的网关节点 |

`hosts/server_master` 由网络初始化代码生成，可能使用以下全部指令和 PEM 块：

| 内容 | 用途 |
| --- | --- |
| `Address` | 网关公网 IP 或可解析地址 |
| `Port` | 网关 Tinc 监听端口 |
| `Subnet` | 网关虚拟 IPv4 地址的 `/32` 路由 |
| `-----BEGIN RSA PUBLIC KEY-----` / `-----END RSA PUBLIC KEY-----` | 网关的 PKCS#1 RSA 公钥；不是私钥 |

下载端不再构造带 `BEGIN PUBLIC KEY` 占位符的 fallback host。后端现在也会在 ZIP 写入前拒绝含有额外指令、非 PKCS#1 RSA 公钥或不完整 PEM 块的 host 文件，不会把它们透传给客户端。

## 与网关配置的一致性

`TincConfigUtils.createTincConf()` 已同样改为 `Mode = router`，新建网络和经后台更新的网络会将网关配置同步为路由模式。已有网关仍可能保存旧的 `Mode = switch`；在使用新版客户端下载前，必须通过网络编辑/重新生成流程让其服务端配置同步为 `router` 并重载网关。未实际连接网关，故这一迁移步骤尚未验证。

## 脱敏回归包

测试包由 `tests/fixtures/generate-tinc-client-config-fixture.ps1` 生成，使用：

- 网络名 `office`、节点名 `client_fixture`；
- IANA 文档地址 `203.0.113.10` 和非生产网段 `10.254.0.1/32`；
- 仅测试用的 PKCS#1 RSA **公钥**。

它不含密码、Token、私钥、可执行文件、DLL、脚本或真实服务器数据。ZIP 结构与本次修改后的下载响应相同：只含 `office/tinc.conf` 和 `office/hosts/server_master`，且没有显式目录条目。

| 项目 | 值 |
| --- | --- |
| 绝对路径 | `D:\\Codes\\Java\\KenDeJi_RuoYi\\RuoYi-Vue-master\\tests\\fixtures\\tinc-client-config.zip` |
| SHA-256 | `EDFEE81D64348D8E889DA22FE1B3FD7B57D8FD7F8913DEE1D4DF0CD80F5858FF` |
| HTTP Content-Type | `application/zip` |
| 响应大小 | 721 字节 |
| ZIP 条目 | `office/tinc.conf`（82 字节）、`office/hosts/server_master`（482 字节） |

## 2026-09-08 实际部署与复测

部署步骤：

1. 执行 `mvn -pl ruoyi-admin -am package -DskipTests`，构建成功。
2. 重启监听本机 `8081` 的后端进程，并确认健康请求返回 HTTP 200。
3. 从本地数据库选择一个非网关测试节点；账号、密码和登录 Token 只存在于测试进程内，未写入本文或保留响应文件。
4. 登录成功后请求 `POST /api/tinc/client/config/download`；下载 ZIP 在临时目录完成检查和 SHA-256 计算后已删除。

实际结果：

| 检查项 | 结果 |
| --- | --- |
| 登录状态 | `1` |
| 下载 HTTP 状态 | `200` |
| Content-Type | `application/zip` |
| ZIP 响应大小 | 1027 字节 |
| ZIP SHA-256 | `4A1C2E1BD43A0C8B8F874239C7A5CF2DBD822214321C76F4AAD64E4354200FB5` |
| 完整 ZIP 条目 | `<netName>/tinc.conf`（74 字节）；`<netName>/hosts/server_master`（820 字节） |
| 显式目录条目 | 无 |
| `tinc.conf` 指令 | `Name`、`Mode`、`AddressFamily`、`ConnectTo` |
| `hosts/server_master` 指令 | `Address`、`Subnet`；`Port` 为可选项，本次网关 host 未配置 |
| PKCS#1 RSA 公钥块 | 存在，未记录正文 |
| 私钥、脚本、EXE、DLL、凭据、说明文件 | 均未出现在 ZIP 条目中 |

## 网关模式迁移

本机保存的 3 份现有网关 `tinc.conf` 均仍为 `Mode = switch`。本次代码已使新建网络和通过后台网络编辑保存的网络生成 `Mode = router`，但不会自行修改已经存在的网关配置。

仍需由具有后台网络编辑权限的管理员逐个执行网络编辑/重新生成流程，并确认该操作通过 SSH 推送新的服务端 `tinc.conf`，随后重载或重启对应网关 Tinc 服务。当前会话没有后台管理会话或远程网关执行凭据，因此没有伪造这一步的完成记录。

尚未验证更新后的网关与客户端建立 Tinc 隧道；应在完成上述迁移后继续完成下载、客户端公钥上传、网关重载和握手验证。

## 2026-09-09 下载接口加固复测

- 数据库存在同 sid 的多条历史/网络记录；下载接口不再使用模糊查询第一条，而是在精确 sid
  记录中选择 Token 所绑定的节点数据库 ID；
- sid 和网络名必须符合客户端相同的安全标识规则；无效 sid 返回 HTTP 400
  `CLIENT_SID_INVALID`，不存在节点返回 404 `CLIENT_NODE_NOT_FOUND`，Token 失效返回 401
  `CLIENT_TOKEN_INVALID`，网络或网关 host 不可用返回对应的 JSON 业务错误；
- ZIP 先在内存中完整构建，成功后一次性发送，避免流式生成异常留下半截 ZIP；
- 隔离节点真实回归仍为 HTTP 200、精确 `Content-Type: application/zip`，并且仍然只有
  `<netName>/tinc.conf` 与 `<netName>/hosts/server_master` 两个文件。没有增加脚本、私钥、
  EXE、DLL、Token、说明文件、`os` 字段或其他 ZIP 条目。

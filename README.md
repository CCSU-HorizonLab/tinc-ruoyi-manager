<p align="center">
	<img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">TincNet Manager v3.9.0</h1>
<h4 align="center">基于 RuoYi 框架的 Tinc VPN 内网集群管理系统</h4>
<p align="center">
	<a href="https://gitee.com/y_project/RuoYi-Vue/stargazers"><img src="https://gitee.com/y_project/RuoYi-Vue/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue"><img src="https://img.shields.io/badge/RuoYi-v3.9.0-brightgreen.svg"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

---

## 平台简介

TincNet Manager 是基于 **RuoYi（若依）v3.9.0** 前后端分离框架二次开发的企业级 **Tinc VPN 内网集群管理平台**。系统实现了 **服务器集群 → Tinc 网络集群 → Tinc 节点** 的层级化管理，支持 RSA 密钥自动生成、Tinc 配置文件自动写入、客户端安装包一键下载等核心运维能力，并集成 C++ Qt 客户端进行公私钥联动。

### 适用场景

- 企业内网 VPN 集群的统一管理与运维
- 多服务器、多网段的 Tinc 节点批量部署
- 内网节点配置的自动化生成与分发
- 内网健康状态的实时监控与告警

---

## 技术架构

### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.5.15 | 基础框架 |
| Spring Security | 5.7.14 | 安全认证与授权 |
| MyBatis | — | ORM 持久层 |
| Redis | — | 会话缓存与数据缓存 |
| JWT | 0.9.1 | 无状态身份认证 |
| Druid | 1.2.27 | 数据库连接池与监控 |
| Swagger (Knife4j) | 3.0.0 | API 接口文档 |
| FastJSON | 2.0.60 | JSON 序列化/反序列化 |
| Apache POI | 4.1.2 | Excel 导入导出 |
| PageHelper | 1.4.7 | MyBatis 物理分页 |
| BouncyCastle | — | RSA 密钥生成（PKCS#1 格式） |
| OSHI | 6.9.1 | 服务器硬件监控 |

### 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 2.6.12 | 前端框架 |
| Element UI | 2.15.14 | UI 组件库 |
| Axios | 0.28.1 | HTTP 请求客户端 |
| Vuex | 3.6.0 | 全局状态管理 |
| Vue Router | 3.4.9 | 前端路由 |
| ECharts | 5.4.0 | 数据可视化图表 |
| Quill | 2.0.2 | 富文本编辑器 |
| JSEncrypt | 3.0.0 | 前端 RSA 加密 |

---

## 核心功能模块

### 1. 🖥️ 服务器集群管理

管理 Tinc VPN 接入服务器的注册与配置。

- 服务器基本信息维护（名称、IP 地址）
- 网段范围配置（起始网段 → 终止网段）
- 端口范围配置（起始端口 → 终止端口）
- 内网数量自动统计
- 服务器状态监控
- Excel 数据导入/导出
- IP 地址格式校验（正则：`xxx.xxx.xxx.xxx`）
- 网段格式校验（正则：`xxx.xxx.xxx` 或 `xxx.xxx`）

**API 端点**：`/tinc/server`、`/manger/mangeServer`

### 2. 🌐 Tinc 内网集群管理

创建和管理 Tinc 虚拟专用网络。

- 内网集群创建（网络名称、接入服务器、端口、网段）
- rootName 自动关联当前登录用户
- 节点数量实时统计
- 内网状态监控（在线/离线）
- Tinc 配置文件自动生成（`tinc.conf`、`tinc-up`/`tinc-down` 脚本）
- 网络环境初始化（目录结构自动创建）
- Excel 数据导入/导出

**API 端点**：`/tinc/network`、`/TincNetworkMange/TincNetworkMange`

### 3. 🔗 Tinc 节点集群管理

管理 Tinc 网络中的各个节点，实现配置的自动化生成与分发。

- 节点信息注册（节点名称、内网 IP、设备 ID、所属内网）
- **RSA 4096 位密钥对自动生成**（BouncyCastle 纯正 PKCS#1 格式，兼容 Tinc）
- **Tinc 配置文件自动写入磁盘**
  - `tinc.conf`（网络配置）
  - `tinc-up` / `tinc-down`（Linux 网络脚本）
  - `tinc-up.bat` / `tinc-down.bat`（Windows 网络脚本）
  - `hosts/<nodeName>`（节点公钥文件）
  - `rsa_key.priv`（节点私钥文件）
- 节点配置状态管理（未配置 / 已配置）
- 节点运行状态监控
- **客户端安装包一键下载**（自动打包配置文件为 ZIP）
- 支持 C++ Qt 客户端公钥上传与配置覆写
- Excel 数据导入/导出

**API 端点**：`/tinc/node`、`/node_mange/node_mange`

#### 节点配置生成流程

```
创建节点 → RSA 4096 密钥生成 → 写入磁盘：
  ├── /etc/tinc/<netName>/tinc.conf          # 网络配置
  ├── /etc/tinc/<netName>/tinc-up            # 启动脚本(Linux)
  ├── /etc/tinc/<netName>/tinc-down          # 停止脚本(Linux)
  ├── /etc/tinc/<netName>/tinc-up.bat        # 启动脚本(Windows)
  ├── /etc/tinc/<netName>/tinc-down.bat      # 停止脚本(Windows)
  ├── /etc/tinc/<netName>/hosts/<nodeName>   # 公钥文件
  └── /etc/tinc/<netName>/rsa_key.priv       # 私钥文件

智能路径：
  开发环境(Win) → D:/tinc/
  生产环境(Linux) → /etc/tinc/
```

### 4. 👤 系统管理

基于 RBAC 的完整权限管理体系。

| 模块 | 功能 |
|------|------|
| 用户管理 | 系统用户 CRUD、角色分配、数据权限 |
| 角色管理 | 角色菜单权限、操作权限、数据范围控制 |
| 菜单管理 | 动态菜单配置、按钮权限标识 |
| 部门管理 | 树形组织机构管理 |
| 岗位管理 | 用户职务配置 |
| 字典管理 | 系统字典数据维护（前端可缓存） |
| 参数管理 | 系统动态参数键值配置 |
| 通知公告 | 系统通知发布与维护 |

### 5. 📊 系统监控

全方位系统运行状态监控。

| 模块 | 功能 |
|------|------|
| 在线用户 | 当前活跃用户 Session 监控与强制下线 |
| 登录日志 | 登录成功/失败记录查询 |
| 操作日志 | 业务操作记录（含请求参数、耗时、IP） |
| 服务监控 | CPU、内存、磁盘、JVM 堆栈实时监控 |
| 缓存监控 | Redis 内存使用、Key 数量、命中率 |
| 连接池监控 | Druid 数据源状态 |

### 6. 🛠️ 系统工具

| 模块 | 功能 |
|------|------|
| 代码生成 | 一键生成 Controller/Service/Mapper/Vue/SQL |
| 系统接口 | Swagger/Knife4j 在线 API 文档 |
| 定时任务 | 基于 Quartz 的任务调度（Cron 表达式） |

### 7. 📈 仪表盘（定制）

- **网络状态统计**：在线率、响应时间、健康评分、故障恢复时间
- **单网监控面板**：针对单个内网的详细监控视图
- ECharts 图表展示（折线图、柱状图、饼图、雷达图）
- 支持 30 秒自动刷新

---

## 项目结构

```
RuoYi-Vue-master
├── ruoyi-admin/                         # 后端主模块（控制器层）
│   └── src/main/java/com/ruoyi/web/controller/
│       ├── MangeServerController.java           # 服务器集群管理
│       ├── TincNetworkMangeController.java      # Tinc 网络集群管理
│       ├── TincNodeMangeController.java         # Tinc 节点集群管理（含下载）
│       ├── common/                              # 公共控制器（登录、验证码、通用上传）
│       ├── monitor/                             # 监控控制器（缓存、服务、日志）
│       ├── system/                              # 系统管理控制器（用户/角色/菜单等）
│       └── tool/                                # 工具控制器（代码生成测试）
│
├── ruoyi-system/                        # 系统服务模块（业务逻辑层）
│   └── src/main/java/com/ruoyi/
│       ├── tinc_server/                         # 服务器集群：domain/mapper/service
│       ├── tinc_network/                        # Tinc 网络集群：domain/mapper/service
│       ├── tinc_node/                           # Tinc 节点集群：domain/mapper/service
│       │   └── service/impl/
│       │       └── TincNodeMangeServiceImpl.java  # ★ 公钥解析与配置覆写逻辑
│       └── system/                              # 系统基础服务
│
├── ruoyi-common/                        # 公共工具模块
│   └── src/main/java/com/ruoyi/common/utils/
│       ├── TincConfigUtils.java                 # ★ Tinc 配置文件生成工具
│       ├── RsaUtils.java                        # ★ RSA 密钥生成（PKCS#1 纯正格式）
│       ├── ZipUtils.java                        # ZIP 打包工具
│       ├── StringUtils.java                     # 字符串工具
│       └── ...                                  # 其他通用工具
│
├── ruoyi-framework/                     # 框架核心模块
│   ├── aspectj/                                 # AOP 切面（日志、权限、数据源）
│   ├── config/                                  # 框架配置（Security、Redis、线程池）
│   ├── datasource/                              # 多数据源支持
│   ├── interceptor/                             # 拦截器（防重复提交）
│   ├── security/                                # Spring Security 安全处理
│   └── web/                                     # Web 层通用配置
│
├── ruoyi-generator/                     # 代码生成模块
├── ruoyi-quartz/                        # 定时任务模块
│
├── ruoyi-ui/                            # 前端 Vue 项目
│   └── src/
│       ├── api/                                  # API 接口定义
│       │   ├── manger/manger.js                  # 服务器管理 API
│       │   ├── TincNetworkMange/TincNetworkMange.js  # Tinc 网络管理 API
│       │   ├── node_mange/node_mange.js          # 节点管理 API
│       │   ├── monitor/                          # 监控 API（含 networkMonitor）
│       │   └── system/                           # 系统管理 API
│       ├── views/                                # 页面视图
│       │   ├── manger/manger/index.vue           # 服务器集群管理页面
│       │   ├── TincNetworkMange/TincNetworkMange/index.vue  # Tinc 网络管理页面
│       │   ├── node_mange/node_mange/index.vue   # 节点集群管理页面
│       │   ├── dashboard/                        # 仪表盘（NetworkStatus, SingleNetWorkControl）
│       │   ├── monitor/                          # 监控页面
│       │   ├── system/                           # 系统管理页面
│       │   └── tinc/                             # Tinc 相关组件
│       ├── components/                           # 公共组件（Crontab 等）
│       ├── layout/                               # 布局组件（侧边栏、导航栏）
│       ├── router/                               # 路由配置（静态路由 + 动态权限路由）
│       ├── store/                                # Vuex 状态管理
│       └── utils/                                # 工具函数（request.js、ruoyi.js）
│
├── sql/                                 # 数据库初始化脚本
│   ├── ry_20250522.sql                          # 基础表结构 + 初始数据
│   └── quartz.sql                               # Quartz 定时任务表
├── model_SQL/                           # 业务模块 SQL（模板参考）
├── doc/                                 # 项目文档
├── bin/                                 # 启动/停止脚本
├── ry.sh                                # Linux 快速启动脚本
├── ry.bat                               # Windows 快速启动脚本
├── pom.xml                              # Maven 父 POM（依赖版本统一管理）
└── README.md
```

---

## 快速开始

### 环境要求

| 组件 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 1.8+ | 推荐 JDK 8 / 11 |
| Node.js | 12+ | 推荐 16 LTS |
| MySQL | 5.7+ | 推荐 8.0 |
| Redis | 3.0+ | 推荐 6.0+ |
| Maven | 3.6+ | — |
| Tinc VPN | 1.0+ | 服务端/客户端均需安装 |

### 后端启动

```bash
# 1. 创建数据库并导入初始化脚本
mysql -u root -p
CREATE DATABASE ry_vue CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ry_vue;
SOURCE sql/ry_20250522.sql;
SOURCE sql/quartz.sql;

# 2. 修改数据库和 Redis 连接配置
vim ruoyi-admin/src/main/resources/application-druid.yml
vim ruoyi-admin/src/main/resources/application.yml

# 3. 编译打包
mvn clean package -DskipTests

# 4. 启动应用
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 或者使用项目自带脚本
sh ry.sh start      # Linux
ry.bat start        # Windows
```

### 前端启动

```bash
cd ruoyi-ui

# 安装依赖
npm install

# 启动开发服务器（默认端口 80）
npm run dev

# 生产构建
npm run build:prod
```

### 访问系统

- 地址：`http://localhost:80`（前端）、`http://localhost:8081`（后端 API）
- 默认账号：`admin` / `admin123`

---

## 业务流程

### 典型运维流程

```
1. 服务器纳管
   添加服务器 → 配置 IP、网段范围、端口范围 → 服务器上线

2. 创建内网
   选择接入服务器 → 填写网络名称、网段、端口 → 自动生成 Tinc 配置文件

3. 添加节点
   选择内网 → 填写节点信息 → 系统自动生成 RSA 密钥对 → 写入 Tinc 配置目录

4. 分发客户端
   下载节点安装包（ZIP） → 解压到客户端 Tinc 目录 → 启动 Tinc 服务

5. 公钥上传（C++ Qt 客户端联动）
   客户端生成密钥 → 上传公钥至服务端 → 服务端覆写 hosts 文件 → 配置完成
```

### 数据层级关系

```
服务器 (MangeServer)
  ├── 网段范围 + 端口范围
  │
  └── Tinc 内网 (TincNetworkMange)
        ├── 所属服务器
        ├── 网段（Subnet）
        │
        └── Tinc 节点 (TincNodeMange)
              ├── 所属内网
              ├── 内网 IP
              ├── RSA 密钥（磁盘存储，不入库）
              └── 配置状态（未配置 → 已配置）
```

---

## 核心特性

### 安全性

- **RBAC 权限模型**：菜单权限 + 按钮权限 + 数据权限三重控制
- **JWT 无状态认证**：支持令牌刷新与过期管理
- **XSS 防护**：请求参数自动过滤（可配置排除路径）
- **SQL 注入防护**：MyBatis 参数化查询
- **密码加密**：BCrypt 加密存储
- **接口防重复提交**：基于 Redis 的幂等性拦截器
- **接口限流**：基于注解的访问频率控制
- **验证码**：数学计算 / 字符验证双模式
- **防盗链**：Referer 白名单校验

### Tinc 专项特性

- **纯正 PKCS#1 格式 RSA 密钥**：BouncyCastle 底层 ASN.1 拼装，非 Java 默认 PKCS#8 换皮，100% 兼容 Tinc
- **跨平台脚本生成**：同时生成 Linux Shell 脚本和 Windows Batch 脚本
- **UTF-8 纯净写入**：NIO 字节流直写，彻底杜绝 CRLF 污染
- **智能路径适配**：开发环境自动使用 `D:/tinc/`，生产环境使用 `/etc/tinc/`
- **公钥精准提取**：正则匹配 PEM 块，过滤脏数据，防止 Subnet 重复叠加
- **架构师防呆设计**：中心服务器不生成 ConnectTo 指令，仅作为被动监听者

### 运维效率

- 一键生成节点全套配置文件
- 客户端安装包自动打包下载
- Excel 批量导入/导出
- 代码生成器（单表 CRUD 一键生成）

---

## API 接口概览

### 服务器集群管理

| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/manger/mangeServer/list` | `manger:manger:list` | 查询服务器列表 |
| GET | `/manger/mangeServer/{Id}` | `manger:manger:query` | 获取服务器详情 |
| POST | `/manger/mangeServer` | `manger:manger:add` | 新增服务器 |
| PUT | `/manger/mangeServer` | `manger:manger:edit` | 修改服务器 |
| DELETE | `/manger/mangeServer/{Ids}` | `manger:manger:remove` | 删除服务器 |
| POST | `/manger/mangeServer/export` | `manger:manger:export` | 导出 Excel |

### Tinc 网络集群管理

| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/TincNetworkMange/TincNetworkMange/list` | `TincNetworkMange:TincNetworkMange:list` | 查询网络列表 |
| GET | `/TincNetworkMange/TincNetworkMange/{id}` | `TincNetworkMange:TincNetworkMange:query` | 获取网络详情 |
| POST | `/TincNetworkMange/TincNetworkMange` | `TincNetworkMange:TincNetworkMange:add` | 新增网络 |
| PUT | `/TincNetworkMange/TincNetworkMange` | `TincNetworkMange:TincNetworkMange:edit` | 修改网络 |
| DELETE | `/TincNetworkMange/TincNetworkMange/{ids}` | `TincNetworkMange:TincNetworkMange:remove` | 删除网络 |
| POST | `/TincNetworkMange/TincNetworkMange/export` | `TincNetworkMange:TincNetworkMange:export` | 导出 Excel |

### Tinc 节点集群管理

| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/node_mange/node_mange/list` | `node_mange:node_mange:list` | 查询节点列表 |
| GET | `/node_mange/node_mange/{id}` | `node_mange:node_mange:query` | 获取节点详情 |
| POST | `/node_mange/node_mange` | `node_mange:node_mange:add` | 新增节点 |
| PUT | `/node_mange/node_mange` | `node_mange:node_mange:edit` | 修改节点（含公钥上传） |
| DELETE | `/node_mange/node_mange/{ids}` | `node_mange:node_mange:remove` | 删除节点 |
| POST | `/node_mange/node_mange/download/{id}` | `node_manage:node_manage:export` | 下载安装包 |
| POST | `/node_mange/node_mange/export` | `node_mange:node_mange:export` | 导出 Excel |

---

## 常见问题

### 1. 客户端安装包下载失败

- 检查 `TincConfigUtils.getBasePath()` 返回的路径下是否存在 `zips/` 目录
- 确认节点对应的 ZIP 文件（命名规则：`<netName>_<nodeName>.zip`）已生成
- 检查文件系统读写权限
- 查看后端日志确认文件路径

### 2. 网段/IP 格式校验不通过

- 服务器 IP：`xxx.xxx.xxx.xxx`（完整 IPv4）
- 网段格式：`xxx.xxx.xxx` 或 `xxx.xxx`
- 确保起始网段 ≤ 终止网段
- 确保起始端口 ≤ 终止端口

### 3. 节点配置状态未更新

- 确认 C++ Qt 客户端已正确调用公钥上传接口
- 检查 `password` 字段传输内容是否包含 `-----BEGIN PUBLIC KEY-----` 头尾标记
- 服务端 hosts 目录中确认公钥文件已生成

### 4. Tinc 节点无法互通

- 检查服务端 `/etc/tinc/<netName>/hosts/` 下各节点的公钥文件是否存在
- 确认各节点 `tinc.conf` 中 `ConnectTo` 指向中心服务器
- 检查防火墙是否放行 Tinc 端口（默认 655）
- 确认 `tinc-up`/`tinc-up.bat` 脚本中的内网 IP 配置正确

### 5. RSA 密钥格式不兼容

- 本系统使用 BouncyCastle 生成纯正 `PKCS#1` 格式
- 公钥头：`-----BEGIN RSA PUBLIC KEY-----`（非 `-----BEGIN PUBLIC KEY-----`）
- Tinc 只认 PKCS#1 格式，若使用其他工具生成需确认格式兼容

---

## 在线体验

| 类型 | 地址 |
|------|------|
| 演示环境 | http://vue.ruoyi.vip |
| 官方文档 | http://doc.ruoyi.vip |

---

## 开发历史

| 提交 | 说明 |
|------|------|
| `6029cad` | 初始化项目（RuoYi 框架基座）+ Tinc 业务初版 |
| `e35d1a5` | 完成 Tinc 内网管理核心逻辑（RSA 生成 + 配置写入 + Shell 调用） |
| `410d9df` | 服务端自动按照配置文件生成客户端配置 |
| `8da51c4` | 修复服务端生成密钥格式问题 |
| `adfc902` | 修复 Java 默认 PKCS#8 格式与 Tinc PKCS#1 格式不兼容问题 |
| `f843d6b` | 与 C++ Qt 客户端建立公私钥联动 |
| `0bbccaf` | 重构变量命名、文件命名、前端展示命名，梳理数据链路 |

---

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

---

## 致谢

- [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) — 若依前后端分离框架，为本项目提供了优秀的架构基础
- [BouncyCastle](https://www.bouncycastle.org/) — 提供纯正 PKCS#1 RSA 密钥生成能力
- [Tinc VPN](https://www.tinc-vpn.org/) — 开源的 Mesh VPN 解决方案

---

> **注意**：本系统在若依框架基础上深度定制了 Tinc VPN 内网集群管理功能，涉及 RSA 密钥生成、配置文件自动写入、Shell 脚本生成、C++ Qt 客户端公钥联动等底层操作。使用前请确保已了解 Tinc VPN 的基本原理和配置方法。

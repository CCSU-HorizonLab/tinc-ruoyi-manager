<p align="center">
	<img alt="logo" src="https://oscimg.oschina.net/oscnet/up-d3d0a9303e11d522a06cd263f3079027715.png">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">RuoYi v3.9.0 - Tinc内网集群管理系统</h1>
<h4 align="center">基于SpringBoot+Vue前后端分离的Tinc内网集群管理平台</h4>
<p align="center">
	<a href="https://gitee.com/y_project/RuoYi-Vue/stargazers"><img src="https://gitee.com/y_project/RuoYi-Vue/badge/star.svg?theme=dark"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue"><img src="https://img.shields.io/badge/RuoYi-v3.9.0-brightgreen.svg"></a>
	<a href="https://gitee.com/y_project/RuoYi-Vue/blob/master/LICENSE"><img src="https://img.shields.io/github/license/mashape/apistatus.svg"></a>
</p>

## 平台简介

本系统是基于若依框架开发的Tinc内网集群管理平台，实现了服务器集群、Tinc网络集群、Tinc节点集群的统一管理。系统采用前后端分离架构，提供完整的用户权限管理、集群监控、节点配置等功能。

### 技术架构

**后端技术栈**
- Spring Boot 2.5.15 - 基础框架
- Spring Security 5.7.14 - 安全框架
- MyBatis - 持久层框架
- Redis - 缓存
- JWT - 权限认证
- Druid - 数据库连接池
- Swagger 3.0.0 - API文档
- FastJSON 2.0.60 - JSON处理
- POI 4.1.2 - Excel导入导出
- PageHelper 1.4.7 - 分页插件

**前端技术栈**
- Vue 2.x - 前端框架
- Element UI - UI组件库
- Axios - HTTP请求
- Vuex - 状态管理
- Vue Router - 路由管理

## 核心功能模块

### 1. 服务器集群管理 (MangeServerController)
- 服务器信息管理（服务器名称、IP地址）
- 网段配置管理（起始网段、终止网段）
- 端口范围配置（起始端口、终止端口）
- 内网数量统计
- 服务器状态监控
- Excel数据导入导出

**API接口**
- `GET /manger/mangeServer/list` - 查询服务器集群列表
- `GET /manger/mangeServer/{Id}` - 获取服务器详细信息
- `POST /manger/mangeServer` - 新增服务器
- `PUT /manger/mangeServer` - 修改服务器信息
- `DELETE /manger/mangeServer/{Ids}` - 删除服务器
- `POST /manger/mangeServer/export` - 导出服务器数据

### 2. Tinc内网集群管理 (TincNetworkMangeController)
- Tinc网络集群创建与管理
- 内网名称与网段配置
- 节点数量统计
- 内网状态监控
- 用户权限关联（rootName自动填充当前登录用户）
- Excel数据导入导出

**API接口**
- `GET /TincNetworkMange/TincNetworkMange/list` - 查询Tinc网络集群列表
- `GET /TincNetworkMange/TincNetworkMange/{id}` - 获取网络集群详细信息
- `POST /TincNetworkMange/TincNetworkMange` - 新增网络集群
- `PUT /TincNetworkMange/TincNetworkMange` - 修改网络集群
- `DELETE /TincNetworkMange/TincNetworkMange/{ids}` - 删除网络集群
- `POST /TincNetworkMange/TincNetworkMange/export` - 导出网络集群数据

### 3. Tinc节点集群管理 (TincNodeMangeController)
- Tinc节点信息管理
- 节点配置（节点名称、内网IP、密码）
- 设备ID管理
- 节点状态监控
- 配置状态管理
- 客户端安装包下载（自动打包配置文件）
- Excel数据导入导出

**API接口**
- `GET /node_mange/node_mange/list` - 查询节点列表
- `GET /node_mange/node_mange/{id}` - 获取节点详细信息
- `POST /node_mange/node_mange` - 新增节点
- `PUT /node_mange/node_mange` - 修改节点信息
- `DELETE /node_mange/node_mange/{ids}` - 删除节点
- `POST /node_mange/node_mange/download/{id}` - 下载客户端安装包
- `POST /node_mange/node_mange/export` - 导出节点数据

### 4. 系统管理模块
- **用户管理**：系统用户配置、角色分配
- **角色管理**：角色菜单权限分配、数据权限控制
- **菜单管理**：系统菜单配置、操作权限、按钮权限
- **部门管理**：组织机构配置、树结构展现
- **岗位管理**：用户职务配置
- **字典管理**：系统字典数据维护
- **参数管理**：系统动态参数配置
- **通知公告**：系统通知发布维护

### 5. 系统监控模块
- **在线用户**：当前活跃用户状态监控
- **登录日志**：系统登录日志记录查询
- **操作日志**：系统操作日志记录查询
- **服务监控**：CPU、内存、磁盘、堆栈等信息监控
- **缓存监控**：Redis缓存信息查询、命令统计
- **连接池监视**：数据库连接池状态监控

### 6. 系统工具模块
- **代码生成**：前后端代码生成（Java、HTML、XML、SQL）
- **系统接口**：自动生成API接口文档
- **定时任务**：在线任务调度管理

## 项目结构

```
RuoYi-Vue-master
├── ruoyi-admin              # 后端主模块
│   └── src/main/java/com/ruoyi/web/controller
│       ├── common           # 公共控制器
│       │   ├── CaptchaController.java
│       │   └── CommonController.java
│       ├── monitor          # 监控控制器
│       │   ├── CacheController.java
│       │   ├── ServerController.java
│       │   ├── SysLogininforController.java
│       │   ├── SysOperlogController.java
│       │   └── SysUserOnlineController.java
│       ├── system           # 系统管理控制器
│       │   ├── SysConfigController.java
│       │   ├── SysDeptController.java
│       │   ├── SysDictDataController.java
│       │   ├── SysDictTypeController.java
│       │   ├── SysIndexController.java
│       │   ├── SysLoginController.java
│       │   ├── SysMenuController.java
│       │   ├── SysNoticeController.java
│       │   ├── SysPostController.java
│       │   ├── SysProfileController.java
│       │   ├── SysRegisterController.java
│       │   ├── SysRoleController.java
│       │   └── SysUserController.java
│       ├── tool             # 工具控制器
│       │   └── TestController.java
│       ├── MangeServerController.java          # 服务器集群管理
│       ├── TincNetworkMangeController.java    # Tinc网络集群管理
│       └── TincNodeMangeController.java       # Tinc节点集群管理
├── ruoyi-system             # 系统服务模块
│   └── src/main/java/com/ruoyi
│       ├── tinc_server     # 服务器集群服务
│       │   ├── domain/MangeServer.java
│       │   ├── mapper/MangeServerMapper.java
│       │   └── service/IMangeServerService.java
│       ├── tinc_network     # Tinc网络集群服务
│       │   ├── domain/TincNetworkMange.java
│       │   ├── mapper/TincNetworkMangeMapper.java
│       │   └── service/ITincNetworkMangeService.java
│       ├── tinc_node        # Tinc节点集群服务
│       │   ├── domain/TincNodeMange.java
│       │   ├── mapper/TincNodeMangeMapper.java
│       │   └── service/ITincNodeMangeService.java
│       └── system           # 系统基础服务
├── ruoyi-common             # 公共模块
│   ├── annotation           # 自定义注解
│   ├── config               # 系统配置
│   ├── constant             # 常量定义
│   ├── core                 # 核心类
│   ├── enums                # 枚举类型
│   ├── exception            # 异常处理
│   ├── filter               # 过滤器
│   └── utils                # 工具类
├── ruoyi-framework          # 框架核心模块
│   ├── aspectj              # AOP切面
│   ├── config               # 配置类
│   ├── datasource           # 数据源配置
│   ├── interceptor          # 拦截器
│   ├── manager              # 异步管理
│   ├── security             # 安全配置
│   └── web                  # Web配置
├── ruoyi-generator          # 代码生成模块
├── ruoyi-quartz             # 定时任务模块
└── ruoyi-ui                 # 前端Vue项目
    ├── src
    │   ├── api              # API接口
    │   │   ├── TincNetworkMange/TincNetworkMange.js
    │   │   ├── manger/manger.js
    │   │   ├── node_mange/node_mange.js
    │   │   ├── monitor/     # 监控API
    │   │   ├── system/      # 系统管理API
    │   │   └── user/user.js
    │   ├── assets           # 静态资源
    │   ├── components       # 公共组件
    │   ├── directive        # 自定义指令
    │   ├── layout           # 布局组件
    │   ├── plugins          # 插件
    │   ├── router           # 路由配置
    │   ├── store            # Vuex状态管理
    │   ├── utils            # 工具函数
    │   └── views            # 页面视图
    │       ├── TincNetworkMange/TincNetworkMange/index.vue
    │       ├── manger/manger/index.vue
    │       ├── node_mange/node_mange/index.vue
    │       ├── monitor/     # 监控页面
    │       ├── system/      # 系统管理页面
    │       └── dashboard/   # 仪表板
    └── package.json
```

## 快速开始

### 环境要求
- JDK 1.8+
- Node.js 12+
- MySQL 5.7+
- Redis 3.0+
- Maven 3.6+

### 后端启动
```bash
# 1. 导入数据库
# 执行 model_SQL 目录下的SQL文件

# 2. 修改配置文件
# 编辑 ruoyi-admin/src/main/resources/application.yml
# 配置数据库、Redis连接信息

# 3. 编译打包
cd ruoyi-admin
mvn clean package

# 4. 启动应用
java -jar target/ruoyi-admin.jar
```

### 前端启动
```bash
# 1. 安装依赖
cd ruoyi-ui
npm install

# 2. 启动开发服务器
npm run dev

# 3. 访问系统
# 浏览器打开 http://localhost:80
```

### 默认账号
- 用户名：admin
- 密码：admin123

## 核心特性

### 1. 权限管理
- 基于RBAC的权限控制模型
- 支持菜单权限、按钮权限、数据权限
- 支持多终端认证（JWT）
- 动态权限菜单加载

### 2. 集群管理
- 服务器集群统一管理
- Tinc网络集群创建与配置
- Tinc节点自动配置与部署
- 客户端安装包一键生成下载

### 3. 监控告警
- 实时服务器状态监控
- 在线用户状态跟踪
- 操作日志与登录日志记录
- 缓存与连接池状态监控

### 4. 开发效率
- 代码生成器一键生成前后端代码
- Swagger自动生成API文档
- 支持Excel数据导入导出
- 完善的异常处理机制

### 5. 安全防护
- XSS攻击防护
- SQL注入防护
- CSRF防护
- 密码加密存储
- 接口防重复提交
- 接口限流

## 在线体验

演示地址：http://vue.ruoyi.vip  
文档地址：http://doc.ruoyi.vip

## 常见问题

### 1. 客户端安装包下载失败
- 检查TincConfigUtils配置的base路径是否正确
- 确认节点配置已生成对应的zip文件
- 检查文件权限

### 2. 网段配置验证失败
- 网段格式应为xxx.xxx.xxx或xxx.xxx
- IP地址格式应为xxx.xxx.xxx.xxx
- 确保起始网段小于终止网段

### 3. 节点连接失败
- 检查服务器IP和端口配置是否正确
- 确认Tinc服务已启动
- 检查防火墙设置

## 技术支持

- 官方文档：http://doc.ruoyi.vip
- 演示地址：http://vue.ruoyi.vip
- 若依前后端分离交流群：详见README底部

## 许可证

本项目基于 Apache 2.0 许可证开源，详见 [LICENSE](LICENSE) 文件。

## 致谢

感谢若依开源项目提供的优秀框架基础。

---

**注意**：本系统在若依框架基础上扩展了Tinc内网集群管理功能，适用于企业内网VPN集群管理场景。使用前请确保已了解Tinc VPN的基本原理和配置方法。

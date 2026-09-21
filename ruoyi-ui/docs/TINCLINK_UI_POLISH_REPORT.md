# TincLink 比赛展示级前端 UI/UX 视觉与交互重塑报告

**项目名称**：TincLink —— 面向跨域设备互联的虚拟网络服务平台  
**执行角色**：前端 UI/UX 专家与工程重构  
**执行目标**：在 100% 保持既有后端接口、Stable-ID 传参契约与运行时业务逻辑的前提下，对管理后台进行比赛展示级视觉、交互与真实性规范化重塑。

---

## 1. 视觉设计原则与 Design System

### 1.1 设计哲学
- **定位**：面向高校科技创新与系统挑战赛的**企业级、专业化、高可信度**网络控制运营中心。
- **色彩规范**：
  - **主色系**：科技深蓝 / 青蓝（Primary: `#0284c7`, Deep: `#0f172a`, Light: `#e0f2fe`）。
  - **状态色规范（严格语义绑定）**：
    - 绿色系（`#10b981` / `#ecfdf5`）：`READY`、`ONLINE`、`LOCAL`、`已配置`（代表真实联通与就绪）。
    - 蓝色系（`#0284c7` / `#e0f2fe`）：`AGENT`、`检查中`。
    - 琥珀橙（`#f59e0b` / `#fffbeb`）：`DEGRADED`、`未配置`（代表降级或警告）。
    - 绯红色（`#ef4444` / `#fef2f2`）：`UNREACHABLE`、`ERROR`、`REVOKED`、`离线`。
    - 中性灰（`#94a3b8` / `#f1f5f9`）：`UNKNOWN`（未知）、未检测状态。
- **容器与排版规范**：
  - **页面背景**：统一采用浅蓝灰 `#f8fafc`。
  - **卡片系统**：统一圆角 `10px`，轻边框 `#e2e8f0`，柔和轻阴影 `0 1px 3px rgba(0,0,0,0.04)`，移除厚重投影与毛玻璃滥用。
  - **组件间距**：按 `14px / 18px / 24px` 节奏建立规范排版。
  - **头部 Banner**：各业务页统一配置 `.tl-page-header`，清晰呈现产品级功能标题、技术定位与核心动作。

---

## 2. 修改页面及范围清单

| 页面模块 | 对应源码文件 | 修改性质 | 核心修改点 |
| :--- | :--- | :--- | :--- |
| **全局设计规范** | `src/assets/styles/tinclink.scss`<br>`src/assets/styles/index.scss`<br>`src/assets/styles/sidebar.scss` | 新增 / 样式注入 | Design Tokens、状态徽标、空状态卡片、响应式断点、侧边栏活动指示条 |
| **品牌与侧边栏** | `src/layout/components/Sidebar/Logo.vue` | 视觉层 | 统一品牌标题为 `TincLink 管理平台`，优化文字间距与图标比例 |
| **登录展示页** | `src/views/login.vue` | 结构与视觉重构 | 55%/45% 双栏布局、剔除 1.78MB 臃肿 AI 假图、纯 SVG 动态网络拓扑线框、保留完整验证码逻辑 |
| **服务器管理** | `src/views/tinc/server/index.vue` | 交互与展示升级 | 增加标准 Header、LOCAL/AGENT 状态徽章、双进度 CPU/MEM 格式化胶囊、表格空状态插槽 |
| **虚拟网络管理** | `src/views/tinc/network/index.vue` | 交互与展示升级 | 标准 Header、网关图标结合展示、READY 状态点指示与检查中 Spinner、严格坚守 Stable-ID |
| **节点身份管理** | `src/views/tinc/node/index.vue` | 语义修正与展示升级 | 消除裸 `0` 错误语义（配置状态映射为“未配置”，无心跳节点映射为“未知”）、网关/网络层级化展示 |
| **单网监控面板** | `src/views/dashboard/SingleNetWorkControl.vue` | 布局重构与诚信标注 | 消除大片空白无物感，重构指引式卡片与三大网络能力架构卡，模拟指标显式标注 `[演示数据]` |
| **网络状态统计** | `src/views/dashboard/NetworkStatus.vue` | 架构升级与数据透明化 | 增加真实资产统计（网络数、网关数、Agent 在线数、节点数），模拟健康/趋势卡片显式标注 `[演示数据]` |

---

## 3. UI 深度审计结论（UI Audit）

1. **登录页问题**：
   - *修改前*：采用 1.78MB 的 AI 生成图 `login-background.jpg`，表面印有虚假的 "TINC MESH VPN TOPOLOGY"、"DYNAMIC ROUTING CONFIGURATION PARAMS" 等文字，充斥低质感与非工程化痕迹；居中狭窄登录框仅显示若依默认标题。
   - *修改后*：重构为 55%（左侧科技品牌与动态 SVG 拓扑）+ 45%（右侧纯白企业管理卡片）双栏布局；SVG 拓扑中真实标注生产集群资产（`Aliyun_tinc`、`Access-A`、`Access-B`、`network1`、`new_network`），呈现专业级网状互联。
2. **Server 页面问题**：
   - *修改前*：无页面引导；运行模式裸露为 `LOCAL` / `AGENT`；CPU/内存拼凑为 `10% / 25.09%`；无空状态提示。
   - *修改后*：增加标准化 Header；运行模式与 Agent 状态采用带状态微动圆点的 Tag；CPU 与内存以独立色阶微胶囊化呈现。
3. **Network 页面问题**：
   - *修改前*：节点数显示裸数字；运行状态检查中无动态反馈；接入服务器未带网关视觉特征。
   - *修改后*：展示为 `2 个节点` 微型徽标；服务器结合网关图标呈现；READY 状态配备常态翠绿呼吸点，检查中配备加载动画。
4. **Node 页面问题**：
   - *修改前*：节点状态与配置状态均展示为裸数字 `0`，给评委严重的数据未处理感。
   - *修改后*：配置状态精准映射为 `未配置` / `已配置`；节点状态在缺失实时探针的情况下实事求是呈现为 `未知`（灰色 Tag），杜绝虚假欺瞒；内网与网关结合展示，大幅节省横向空间。
5. **单网监控面板问题**：
   - *修改前*：进入页面仅有一个居中小卡片，下方 70% 屏幕呈现大片白色死区；选定网络后展示的响应时间与丢包属于随机假数据。
   - *修改后*：重构顶部选择控制器，下方填充专业级的“接入服务器绑定”、“公钥路由与节点准入”、“实时探针与就绪监测”引导式架构卡；选定网络后，对随机指标全面标注 `[演示数据]` 标签，达到工程透明可信。
6. **网络状态统计问题**：
   - *修改前*：页面核心指标充斥大量 `new Random()` 伪造数据，极易在比赛答辩被追问数据源时失分。
   - *修改后*：顶部优先增加 4 项 100% 真实的生产资产指标（真实网络总数、READY 数量、Access Server 在线 Agent 数量、节点配置数）；下方分析卡片显式标注 `[演示数据 / 仿真模型]`。

---

## 4. 模拟数据审计与真实性处理对照表

严格遵照指导原则 XVIII（真实 > 少量真实指标 > 大量假的漂亮指标）：

| 监测项 | 数据源性质 | 修改前展示方式 | 修改后处理策略 | 方案归属 |
| :--- | :--- | :--- | :--- | :--- |
| **虚拟网络总数** | 真实 DB 汇总 (`ITincNetworkMangeService`) | 嵌套在圆环进度条侧边 | 提升为顶部核心真实资产第一卡片展示（含 READY 数量） | **方案 A (真实保留与突出)** |
| **Access Server 集群** | 真实 DB 汇总 (`ITincServerService`) | 原页面未展示该宏观指标 | 提升为顶部核心真实资产第二卡片（真实 ONLINE Agent 数） | **方案 A (真实新提炼)** |
| **网络设备节点** | 真实 DB 汇总 (`ITincNodeMangeService`) | 原页面未展示该宏观指标 | 提升为顶部核心真实资产第三卡片（真实已配置节点数） | **方案 A (真实新提炼)** |
| **网络在线率** | 真实 DB 运算 (`onlineNetworks / totalNetworks`) | 圆环百分比 | 保留为第四真实指标卡片，标明核心路由连通率 | **方案 A (真实保留)** |
| **平均响应时间** | 后端随机数 (`30 + Random(20)`) | 假装为“最近7天平均”真实探针 | 保留指标，但右上角强制添加 `[演示数据]` 标签 | **方案 B (明确标注 Demo)** |
| **网络健康评分** | 后端随机数 (`80 + Random(16)`) | 假装为系统权威打分 | 标明 `多维度仿真评估`，并添加 `[演示数据]` 标签 | **方案 B (明确标注 Demo)** |
| **故障恢复时间** | 后端随机数 (`10 + Random(10)`) | 假装为 SLA 真实追踪 | 标明 `SLA 恢复模型`，并添加 `[演示数据]` 标签 | **方案 B (明确标注 Demo)** |
| **7天中断次数趋势** | 后端随机数组 (`rand.nextInt(3)`) | 折线图无标识 | 图表表头清晰增加 `[演示数据]` 标签与说明 Tooltip | **方案 B (明确标注 Demo)** |
| **单网速率与吞吐** | 后端随机数 | 表盘直接显示具体 Mbps | 指标卡表头强制增加 `[演示数据]` 标签 | **方案 B (明确标注 Demo)** |

---

## 5. Stable-ID 契约完整性回归验证

本轮重构全程贯彻**绝对不破坏业务逻辑、绝对不回退 Stable-ID**准则。

### 自动化契约测试验证结果
执行命令：`npm run test:stable-id`
```text
TAP version 13
# Subtest: runtime status API and active network page use stable network IDs
ok 1 - runtime status API and active network page use stable network IDs
# Subtest: runtime query failures are not presented as a real NOT_READY result
ok 2 - runtime query failures are not presented as a real NOT_READY result
# Subtest: active selectors display names but submit stable IDs
ok 3 - active selectors display names but submit stable IDs
# Subtest: name fields remain available only for display and search
ok 4 - name fields remain available only for display and search
# Subtest: application routes use the stable-ID management pages
ok 5 - application routes use the stable-ID management pages
1..5
# tests 5
# pass 5
# fail 0
```
- [x] Network 运行状态刷新依然严格传递 `scope.row.id`（Long），未回退到 `networkName`。
- [x] Network 查询失败状态仍然保持 `UNKNOWN` 和 `STATUS_QUERY_FAILED`，未伪造 `TINC_RUNTIME_NOT_READY`。
- [x] 所有下级选择器继续保持 `label=server.serverName, value=server.id` 以及 `label=network.networkName, value=network.id`。
- [x] 搜索过滤字段保留纯前端搜索过滤用途，无参数污染。

---

## 6. 生产编译与打包验证（Production Build）

执行命令：`npm run build:prod`
```text
-  Building for production...
 DONE  Build complete. The dist directory is ready to be deployed.
```
- **构建结果**：0 Errors，0 Breaking Warnings。
- **资源优化**：成功剥离了原 1.78MB 的 AI 背景图片，生产静态包尺寸大幅缩减，首屏渲染与静态加载显著加速。

---

## 7. 响应式与跨分辨率适配

针对 `1920×1080`（标准桌面/大屏投影）及 `1366×768`（竞赛现场笔记本屏幕）进行了深度针对性优化：
1. **1366×768 笔记本屏幕**：
   - 表格所有核心列预设合理 `min-width`，表格容器支持丝滑横向平移，绝不截断或折行挤坏 IP、CIDR 与状态文字。
   - 顶部 Page Header 与统计卡片自动折叠为紧凑两列排布，各内边距从 `24px` 优化为 `16px~18px`。
2. **1920×1080 评委答辩投影屏**：
   - 页面背景、白色卡片、翠绿色状态呼吸灯与青蓝色主调产生鲜明对比，在大屏投影环境下清晰可读。
   - 单网监控面板与登录页拓扑图在全屏展示下构图平稳大气，消除此前两侧悬空、中间局促的违和感。

---

## 8. 未解决项与未来演进建议

1. **终端心跳探针支持**：当前后端未实现客户端节点的持续心跳反馈接口，前端实事求是将其展示为“未知”。未来若平台实现客户端 Agent 或 TUN 接口流量捕获，可在后端增加 Node 心跳上报机制。
2. **实际链路延迟打通**：当前平均响应时间与中断趋势标注为演示数据。后续如需转为全真实数据，建议后端编写轻量定时任务通过 `fping` 对各个活跃 Node 进行 ICMP 探活并落库。

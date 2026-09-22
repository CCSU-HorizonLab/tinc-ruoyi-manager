# TincLink AIOps 智能运维专家设计与实现报告

## 1. 功能定位
- **官方名称**：`TincLink AIOps 智能运维专家`
- **副标题**：`网络故障诊断与排障辅助`
- **核心定位**：专为 TincLink 虚拟组网、接入服务器群与分布式边缘节点设计的比赛展示级智能化排障诊断入口。它不是普通闲聊式的 ChatBot，也不将模拟规则伪装为大模型推理，而是深度面向虚拟网络底层运行故障的专业诊断助手。
- **重点覆盖故障场景**：
  1. Windows 客户端虚拟适配器（TAP-Windows / Wintun）无法打开或无法连接接入端；
  2. Access Server 接入服务不可达、公网 Tinc 端口受限；
  3. Network Runtime 处于 `NOT_READY`、端口冲突或 `tinc-up` 执行权限异常；
  4. Access Agent 探针状态显示 `UNREACHABLE`、心跳超时或 Secret 鉴权异常；
  5. Tinc 守护进程启动崩溃、退出码 `status=1/FAILURE`；
  6. RSA/Ed25519 Public Key 未生效、双向同步缺失或已被管理员 `REVOKED` 吊销；
  7. 客户端节点无法 Ping 通虚拟网关、Linux 内核 `ip_forward` 转发缺失或防火墙阻断；
  8. 路由丢失、CIDR 掩码配置冲突、配置文件语法错误等。

---

## 2. UI 结构
- **右下角唤醒浮动按钮 (`aiops-fab`)**：
  - 固定定位：`bottom: 28px; right: 28px; z-index: 1999;`，不遮挡表格分页、图表工具或批量操作。
  - 动态交互：收起状态下为精致胶囊 `[CPU图标] AIOps`，鼠标 Hover 时顺畅展开为 `AIOps 智能诊断`。
  - 抽屉开启联动：当排障抽屉打开时，浮动按钮自动隐藏（`v-show="!drawerVisible"`），界面清爽专注。
- **右侧抽屉 (`aiops-drawer`) 响应式规范**：
  - 1920 桌面端：`480px`
  - 1366 常见笔记本端：`420px`
  - `<= 768px` 移动端：`100%` 全屏自适应
- **抽屉顶部 Header**：
  - 左侧：品牌青蓝高亮图标 (`el-icon-data-analysis`)；
  - 主副标题：`TincLink AIOps 智能运维专家` + `网络故障诊断与排障辅助`；
  - 真实状态标签：展示 `[CPU图标] 专家规则诊断引擎`（未配置外部大模型时不伪造 `ONLINE`，真实展示规则引擎就绪状态）。

---

## 3. 输入模型
- **故障描述输入项 (`queryForm.issue`)**：
  - 长度限制：`1 ~ 200` 字，配备 Element UI 原生 `maxlength="200"` 与 `show-word-limit` 字数统计。
  - 占位符提示：`例如：Windows 节点无法连接 Ubuntu 接入服务器，或 DEV_B 显示 READY 但客户端无法 Ping 通网关`。
  - 支持一键清除 (`clearable`)。

---

## 4. 日志输入
- **系统报错日志 / 终端输出文本框 (`queryForm.logs`)**：
  - 规格：7 行文本高度，采用等宽字体家族（`'Consolas', 'Monaco', 'Courier New', monospace`），确保终端堆栈与输出列对齐。
  - 覆盖场景：支持完整粘贴 `Tinc log`、`systemctl status`、`Windows Agent log`、`Ping 输出`、`Runtime error` 及 `Management error`。
  - 便捷操作栏：
    - **示例日志**：一键装载真实系统级典型报错片段；
    - **清空日志**：一键清空日志文本与脱敏统计提示。

---

## 5. 诊断上下文 (Stable-ID 关联)
- **折叠面板设计**：采用 `<el-collapse>` 可折叠区域，避免占用首屏空间。
- **关联数据源**：仅读取当前管理端已经拥有的公开拓扑状态数据：
  - **关联接入服务器 (Server)**：选择项展示 `serverName (ID: {id})`，绑定 Stable `serverId`；
  - **关联虚拟网络 (Network)**：选择项展示 `networkName (ID: {id})`，绑定 Stable `networkId`；
  - **关联节点 (Node)**：选择项展示 `nodeName (ID: {id})`，绑定 Stable `nodeId`。
- **安全隔离**：严禁自动将 `Agent Secret`、`Token`、`密码`、`私钥 (Private Key)` 或 `验证码` 序列化传入诊断接口。

---

## 6. 敏感信息保护 (Client-Side Redaction)
- **独立脱敏模块**：[`src/utils/aiopsSanitizer.js`](file:///D:/Codes/Java/KenDeJi_RuoYi/RuoYi-Vue-master/ruoyi-ui/src/utils/aiopsSanitizer.js)
- **前端本地脱敏能力**：在网络请求发出前，于浏览器客户端内存执行多模式正则表达式扫描与脱敏置换：
  1. `Private Key PEM`（PKCS#8、PKCS#1 RSA、EC、OPENSSH、DSA）：全部置换为 `[REDACTED: PRIVATE KEY]`；**绝对禁止将任何私钥发送到外部**；
  2. `Bearer Token`：置换为 `Bearer [REDACTED]`；
  3. `JWT`（三段式 Base64 签名串）：置换为 `[REDACTED: JWT]`；
  4. `Authorization Header`：置换为 `Authorization: [REDACTED]`；
  5. `password=` / `passwd:` / `pwd=`：字段值置换为 `[REDACTED]`；
  6. `secret=` / `agentSecret=` / `secretKey=`：字段值置换为 `[REDACTED]`；
  7. `Cookie` / `Set-Cookie`：头部值置换为 `[REDACTED]`。
- **界面安全感知**：一旦检测到脱敏，页面自动呈现绿色安全提示条：`已自动执行安全脱敏：过滤 X 处敏感凭据/密钥并置换为 [REDACTED]`。

---

## 7. 诊断结果结构
诊断结果绝不以单一晦涩的纯 Markdown 大文本堆砌，而是划分为结构分明、专业可信的五大模块：
1. **诊断结论 (Summary Card)**：提炼高层结论（如“Windows 节点虚拟网卡驱动或路由策略异常，导致无法建立安全隧道”），标注规则诊断标签与来源知识库；
2. **置信度 (Confidence)**：若后端返回置信度，真实映射为 `高 / 中 / 低`；若无，绝不随机捏造 `95%` 或 `88%` 等伪造百分比；
3. **可能原因分析 (Possible Causes Card)**：结构化有序列表，带独立序号圆标，罗列排查优先级；
4. **建议检查与排障命令 (Checks & Commands Card)**：
   - 针对每个检查项提供明确的排障操作；
   - 排障命令统一封装于暗黑高对比度代码块（`#1e293b`），支持“一键复制命令”至系统剪贴板；
   - **严格声明**：`AIOps 仅建议排障命令，系统严禁远程执行任意 Shell，请由运维人员核实后手动执行`；
5. **风险提示与注意事项 (Warnings Card)**：高亮黄色警示卡片，提醒如服务重启导致短暂断连、公钥重载要求等；
6. **诊断依据 (Evidence Card)**：展示根据当前网络上下文采集的真实拓扑状态标签（例如 `接入服务器 Access-B (ID: 1) [探针: READY]`）。

---

## 8. 快捷问题与空状态
- **优化后的空状态引导文案**：
  > “描述故障现象并粘贴相关日志，AIOps 将结合当前 TincLink 网络状态提供排障建议。”
- **4 个开箱即用的快捷排障诊断模板**：
  1. `Windows 客户端无法连接`（TAP/Wintun 驱动、防火墙及握手超时场景）；
  2. `Network 为什么显示 NOT_READY？`（systemd 单元失败、655 端口占用与 tinc-up 权限）；
  3. `Access Agent 为什么 UNREACHABLE？`（9091 探针端口、安全组、守护进程存活）；
  4. `节点为什么 Ping 不通网关？`（内核转发参数、虚拟接口 IP 与 FORWARD 链策略）。
- 点击任一模板，自动将典型故障描述与真实报错日志填充至输入区域。

---

## 9. 错误处理与超时保障
- **防抖与防重复提交**：诊断进行时按钮处于 `loading` 禁用态，文本变更为 `正在分析网络状态与日志...`；
- **慢响应体验**：若网络请求等待超过 7 秒，自动弹出友情提示：`诊断耗时较长，正在分析拓扑规则，请继续等待或稍后重试...`；
- **故障优雅降级**：若后端 `/tinc/aiops/diagnose` 未就绪或发生脱机，前端绝不白屏、绝不弹出无法理解的堆栈，而是自动平滑切换至 TincLink 内置专家知识库规则引擎，并给予用户真实提示；
- **状态保留**：出错或重新尝试时，始终完好保留用户的输入现象与日志。

---

## 10. API 契约设计 (API Contract)
标准接口模块：[`src/api/tinc/aiops.js`](file:///D:/Codes/Java/KenDeJi_RuoYi/RuoYi-Vue-master/ruoyi-ui/src/api/tinc/aiops.js)

### 请求契约
- **Path**：`POST /tinc/aiops/diagnose`
- **Request Body**：
```json
{
  "issue": "Windows 节点无法连接 Ubuntu 接入服务器",
  "logs": "Cannot open TAP-Win32/Wintun device...",
  "context": {
    "serverId": 1,
    "networkId": 2,
    "nodeId": 3
  }
}
```
*严格规范：Context 中只允许使用稳定数字主键 (`serverId`, `networkId`, `nodeId`)，绝不允许使用动态名称作为主键。*

### 响应契约
- **Response Body**：
```json
{
  "code": 200,
  "data": {
    "engine": "RULE_BASED",
    "summary": "Windows 节点虚拟网卡驱动或路由策略异常...",
    "confidence": "HIGH",
    "possibleCauses": [
      "TAP-Windows 或 Wintun 虚拟网卡驱动未正确安装或处于禁用状态"
    ],
    "checks": [
      {
        "title": "检查 Windows 虚拟适配器状态",
        "description": "以管理员身份打开 PowerShell 检查网卡...",
        "command": "Get-NetAdapter | Where-Object { $_.InterfaceDescription -match \"TAP|Wintun\" }"
      }
    ],
    "warnings": [
      "安装或重启虚拟网卡驱动需要管理员权限"
    ],
    "evidence": [
      "接入服务器 Access-B (ID: 1) [探针: READY]",
      "虚拟网络 DEV_B (ID: 2) [运行态: READY | 网段: 10.0.0.0/24]"
    ]
  }
}
```

---

## 11. 当前是否真实 AI 与真实现状
- **真实现状**：本阶段**未接入外部大模型提供方**。
- **诚信声明原则**：前端明确标示当前为 `[规则诊断]` 与 `专家规则诊断引擎`，绝不将预设规则伪造成 DeepSeek/OpenAI 大模型实时生成的分析结果。

---

## 12. 后续模型接入方式 (扩展架构)
- 前端已将请求契约抽象为 [`src/api/tinc/aiops.js`](file:///D:/Codes/Java/KenDeJi_RuoYi/RuoYi-Vue-master/ruoyi-ui/src/api/tinc/aiops.js)。
- 当后续在后端引入大语言模型（如 DeepSeek、OpenAI、Gemini 或私有部署的 Ollama/vLLM）时：
  1. 后端实现 `AiOpsDiagnosticService` 接口与对应的 `LlmDiagnosticService` 实现类；
  2. 后端 RAG 引擎挂载 TincLink 虚拟网络拓扑知识库并输出一致的结构化 JSON；
  3. 前端界面无需任何 DOM 改动，天然完美解析后端返回的结构化结论。

---

## 13. 安全边界
1. **禁止任意命令执行**：严禁新增 `/exec`、Web Terminal 或远程 Shell 触发端点。AIOps 系统仅输出推荐排障命令，用户只能点击“复制命令”并在合规终端中人工复核执行；
2. **严防 XSS 注入**：所有排障命令、结论与用户输入均通过 Vue 原生插值表达式与严格的文本转义渲染，杜绝直接通过 `v-html` 注入不可信字符串；
3. **敏感凭证拦截**：私钥 PEM 块、JWT、Bearer Token、Secret 均在发起诊断前强制客户端脱敏。

---

## 14. 自动化测试验证
新增专用测试套件：[`tests/aiops-contract.test.cjs`](file:///D:/Codes/Java/KenDeJi_RuoYi/RuoYi-Vue-master/ruoyi-ui/tests/aiops-contract.test.cjs)
执行 `npm test`，全部 11 项测试 100% 通过：
- `Subtest: AIOps client-side sanitizer redacts all sensitive credentials and private keys` (PASS)
- `Subtest: AIOps API contract strictly uses Stable IDs (serverId, networkId, nodeId)` (PASS)
- `Subtest: AIOps diagnostic results enforce structured output with evidence and checks` (PASS)
- `Subtest: AIOps strictly forbids shell execution and unsafe raw HTML injection` (PASS)
- `Subtest: AIOps provides empty state with required 4 quick diagnostic templates` (PASS)
- `Subtest: AIOps drawer respects responsive sizing and floating button toggle` (PASS)
- `Subtest: runtime status API and active network page use stable network IDs` (PASS)
- `Subtest: runtime query failures are not presented as a real NOT_READY result` (PASS)
- `Subtest: active selectors display names but submit stable IDs` (PASS)
- `Subtest: name fields remain available only for display and search` (PASS)
- `Subtest: application routes use the stable-ID management pages` (PASS)

---

## 15. 生产构建 (Build:prod)
执行 `npm run build:prod`，前端编译通过，生成静态产物至 `dist/` 目录，无任何语法冲突或打包错误。

# TincNet Manager - C++ Qt 客户端打包与外部测试指南

本指南旨在指导您如何配置、打包 C++ Qt 客户端，并提供给外部测试人员进行联调测试。由于您的 Java 后端与 Tinc 服务器中心部署在同一台物理服务器上，目前的架构非常适合进行单服闭环测试。

---

## 一、 打包发布前的客户端配置 (C++ 侧修改)

在打包 Qt 客户端之前，请让 Qt Agent 确认并修改以下配置：

### 1. 服务器地址（Base URL）
- **修改点**：将客户端代码中的 API 服务器请求前缀（Base URL）从本地测试地址（如 `http://localhost:8081`）修改为您的**真实公网服务器 IP 端口**：
  ```cpp
  // 示例：修改为您的若依后台真实访问路径
  QString baseUrl = "http://<您的服务器公网IP>:<后端端口>/api/tinc/client";
  ```
- **安全提示**：请确保服务器安全组/防火墙已开放该后端端口。

### 2. 接口路径匹配（必须对应新 RESTful API）
请确保 `DaemonServer.cpp` 或相关网络请求类中的请求路径已完全替换为新定义的 RESTful 接口：
- 登录接口：`POST` $\rightarrow$ `/login`
- 下载配置：`POST` $\rightarrow$ `/config/download`
- 上传公钥：`POST` $\rightarrow$ `/key/upload`
- 状态上报：`POST` $\rightarrow$ `/status/update`
- 心跳接口：`POST` $\rightarrow$ `/keepalive`

---

## 二、 Windows 平台打包步骤 (Qt 部署)

为了让测试人员的电脑上无需安装 Qt 开发环境也能直接运行客户端，必须进行**动态库打包（Dependency Deployment）**：

### Step 1 — 生成 Release 版本
在 Qt Creator 左下角将编译模式从 `Debug` 切换为 `Release`，然后运行一次构建，生成 Release 版本的 `.exe` 可执行文件。

### Step 2 — 使用 `windeployqt` 工具收集依赖
1. 在 Windows 开始菜单中打开对应的 Qt 命令行工具（例如：`Qt 5.15.2 (MinGW 8.1.0 64-bit)`）。
2. 使用 `cd` 命令进入到您编译出来的 Release 目录下（包含 `tinc-client.exe` 的那个文件夹）。
3. 执行以下部署命令，工具会自动将所有需要的 Qt 动态链接库（`.dll`）和插件复制到该目录下：
   ```cmd
   windeployqt tinc-client.exe
   ```

### Step 3 — 整合 Tinc 核心文件与驱动
在您的 Release 包文件夹中，需要包含以下配套文件才能使 Tinc 正常工作：
1. **`tincd.exe`**：Tinc 核心守护进程（可以直接从服务器的 `D:/tinc/tincd.exe` 复制）。
2. **`tap-win64` 驱动安装包**：由于 Tinc 需要在 Windows 上创建虚拟网卡（TAP 适配器），测试人员必须提前安装该驱动。请将 `D:/tinc/tap-win64` 文件夹或其安装程序一并放入压缩包。

### Step 4 — 打包压缩
将整个包含 `tinc-client.exe`、Qt 依赖 DLL、`tincd.exe` 以及 `tap-win64` 驱动文件夹的目录，打包压缩为 **`tinc-client-test.zip`**。

---

## 三、 测试人员操作手册 (给外部测试者的测试步骤)

请将以下步骤发给您的外部测试人员：

### Step 1 — 安装虚拟网卡驱动 (首次测试必须)
1. 解压收到的 `tinc-client-test.zip` 压缩包。
2. 打开 `tap-win64` 文件夹，右键选择安装程序并以**管理员身份运行**，完成 TAP 虚拟网卡驱动的安装。

### Step 2 — 创建测试账号（由您在若依后台完成）
1. 您在若依管理后台中，新建一个 Tinc 节点。
2. 记录下分配的：**节点名称（Node Name）** 和 **密码（Password）**，发给测试人员。

### Step 3 — 运行客户端
1. 找到 `tinc-client.exe`，必须**右键选择“以管理员身份运行”**（因为 Tinc 建立通道、配置虚拟网卡 IP 均需要系统管理员权限）。
2. 输入您提供的 **节点名称** 和 **密码**，点击 **“登录”**。

### Step 4 — 自动初始化与联通验证
1. 登录成功后，客户端会自动向服务器请求下载初始化配置，并解压至本地。
2. 客户端在本地自动生成 RSA 4096 密钥，并将公钥上传到您的服务器。
3. 客户端在本地拉起 Tinc 服务。
4. **验证方法**：
   - 打开 Windows 命令行（CMD），输入 `ipconfig`，检查是否多出了一个名为 `tinc0` 的网络适配器，且其 IP 地址为若依后台分配的局域网 IP（例如 `10.0.10.x`）。
   - 在 CMD 中尝试 ping 服务器的虚拟 IP（例如 `ping 10.0.10.1`），或者 ping 其他已连接测试人员的虚拟 IP。若能 ping 通，说明 VPN 隧道已成功建立！

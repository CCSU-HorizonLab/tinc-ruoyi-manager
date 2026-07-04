package com.ruoyi.common.transport;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 JSch SSH/SCP 的 Tinc 配置传输实现
 * <p>
 * 通过 SSH 协议将配置文件推送到远程 Tinc VPN 网关服务器，
 * 并在推送完成后重载 tincd 服务。
 * </p>
 * <p>
 * 凭证优先级：MangeServer 表逐台配置 > application.yml 全局默认值
 * </p>
 *
 * @author Sun
 * @date 2026-07-03
 */
public class SshTincConfigTransport implements TincConfigTransport {

    private static final Logger log = LoggerFactory.getLogger(SshTincConfigTransport.class);

    /** SSH 连接超时 (毫秒) */
    private int connectTimeout = 10000;

    /** 全局默认 SSH 端口（当 MangeServer 未配置时使用） */
    private int defaultSshPort = 22;

    /** 全局默认 SSH 用户 */
    private String defaultSshUser = "root";

    /** 全局默认 SSH 私钥路径 */
    private String defaultSshKeyPath;

    /** 全局默认 SSH 密码 */
    private String defaultSshPassword;

    // ==================== Setter 注入（Spring Bean 配置） ====================

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setDefaultSshPort(int defaultSshPort) {
        this.defaultSshPort = defaultSshPort;
    }

    public void setDefaultSshUser(String defaultSshUser) {
        this.defaultSshUser = defaultSshUser;
    }

    public void setDefaultSshKeyPath(String defaultSshKeyPath) {
        this.defaultSshKeyPath = defaultSshKeyPath;
    }

    public void setDefaultSshPassword(String defaultSshPassword) {
        this.defaultSshPassword = defaultSshPassword;
    }

    // ==================== 核心 SSH 连接管理 ====================

    /**
     * 获取 SSH Session
     * <p>优先使用 MangeServer 表中配置的凭证，否则使用全局默认值</p>
     */
    private Session getSession(String host, Integer port, String user,
                               String keyPath, String password) throws JSchException {
        JSch jsch = new JSch();

        int actualPort = (port != null && port > 0) ? port : defaultSshPort;
        String actualUser = (user != null && !user.isEmpty()) ? user : defaultSshUser;

        // 私钥认证优先
        String actualKeyPath = (keyPath != null && !keyPath.isEmpty()) ? keyPath : defaultSshKeyPath;
        if (actualKeyPath != null && !actualKeyPath.isEmpty()) {
            jsch.addIdentity(actualKeyPath);
        }

        Session session = jsch.getSession(actualUser, host, actualPort);
        session.setTimeout(connectTimeout);

        // 如果没有配置私钥，则使用密码认证
        String actualPassword = (password != null && !password.isEmpty()) ? password : defaultSshPassword;
        if ((actualKeyPath == null || actualKeyPath.isEmpty()) && actualPassword != null && !actualPassword.isEmpty()) {
            session.setPassword(actualPassword);
        }

        // 跳过 host key 校验（生产环境建议配置 known_hosts）
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect(connectTimeout);
        return session;
    }

    // ==================== TincConfigTransport 接口实现 ====================

    @Override
    public void pushFile(String targetHost, String remotePath, String content, boolean executable) {
        log.info("SSH 推送文件 → {}:{}", targetHost, remotePath);
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = getSession(targetHost, null, null, null, null);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(connectTimeout);

            // 确保父目录存在
            String parentDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
            mkdirsSftp(sftp, parentDir);

            // 推送文件内容
            String safeContent = content.replace("\r\n", "\n");
            ByteArrayInputStream bis = new ByteArrayInputStream(
                    safeContent.getBytes(StandardCharsets.UTF_8));
            sftp.put(bis, remotePath, ChannelSftp.OVERWRITE);
            bis.close();

            // 设置可执行权限
            if (executable) {
                sftp.chmod(0755, remotePath);
            }

            log.info("SSH 推送成功 → {}:{}", targetHost, remotePath);
        } catch (Exception e) {
            log.error("SSH 推送失败 → {}:{}", targetHost, remotePath, e);
            throw new RuntimeException("无法推送配置文件到 Tinc 网关 [" + targetHost + "]: " + e.getMessage(), e);
        } finally {
            if (sftp != null && sftp.isConnected()) sftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    @Override
    public void pushDirectory(String targetHost, String remoteDir, Map<String, String> files) {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String filePath = remoteDir + "/" + entry.getKey();
            boolean isExec = filePath.endsWith("tinc-up") || filePath.endsWith("tinc-down");
            pushFile(targetHost, filePath, entry.getValue(), isExec);
        }
    }

    @Override
    public void reloadTinc(String targetHost, String netName) {
        String cmd = "tincd -n " + netName + " -kHUP";
        execCommand(targetHost, cmd);
    }

    @Override
    public void restartTinc(String targetHost, String netName) {
        String cmd = "systemctl restart tinc@" + netName;
        execCommand(targetHost, cmd);
    }

    @Override
    public boolean isTincInstalled(String targetHost) {
        // which tincd → 返回 0 = 已安装，非 0 = 未安装
        CommandResult result = execCommandWithResult(targetHost, "which tincd 2>/dev/null && tincd --version 2>/dev/null || echo 'NOT_FOUND'");
        boolean installed = !result.output.contains("NOT_FOUND") && result.exitStatus == 0;
        log.info("Tinc 安装检测 [{}] → {}", targetHost, installed ? "已安装" : "未安装");
        return installed;
    }

    @Override
    public String installTinc(String targetHost) {
        log.info("开始自动安装 Tinc [{}]...", targetHost);

        // 1. 检测包管理器
        CommandResult pmResult = execCommandWithResult(targetHost,
                "if command -v apt-get >/dev/null 2>&1; then echo 'apt'; " +
                "elif command -v yum >/dev/null 2>&1; then echo 'yum'; " +
                "elif command -v dnf >/dev/null 2>&1; then echo 'dnf'; " +
                "else echo 'unknown'; fi");

        String pkgManager = pmResult.output.trim();
        log.info("检测到包管理器: {}", pkgManager);

        // 2. 根据包管理器执行安装
        String installCmd;
        switch (pkgManager) {
            case "apt":
                installCmd = "apt-get update -qq && apt-get install -y tinc";
                break;
            case "yum":
                installCmd = "yum install -y epel-release && yum install -y tinc";
                break;
            case "dnf":
                installCmd = "dnf install -y epel-release && dnf install -y tinc";
                break;
            default:
                throw new RuntimeException("无法识别目标机器的包管理器，请手动安装 tinc: apt-get install tinc 或 yum install tinc");
        }

        CommandResult installResult = execCommandWithResult(targetHost, installCmd);
        log.info("Tinc 安装完成 [{}], exit={}", targetHost, installResult.exitStatus);

        if (installResult.exitStatus != 0) {
            throw new RuntimeException("Tinc 安装失败 ["
                    + targetHost + "]: " + installResult.output);
        }

        // 3. 创建 /etc/tinc 基础目录结构
        execCommandWithResult(targetHost, "mkdir -p /etc/tinc && echo 'OK'");
        log.info("Tinc 环境初始化完成 [{}]", targetHost);

        return installResult.output;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 递归创建 SFTP 远程目录（等效于 mkdir -p）
     */
    private void mkdirsSftp(ChannelSftp sftp, String dir) {
        String[] dirs = dir.split("/");
        StringBuilder path = new StringBuilder("/");
        for (String d : dirs) {
            if (d.isEmpty()) continue;
            path.append(d).append("/");
            try {
                sftp.mkdir(path.toString());
            } catch (Exception ignored) {
                // 目录已存在则忽略
            }
        }
    }

    /**
     * 在远程网关上执行 Shell 命令
     */
    private void execCommand(String targetHost, String command) {
        CommandResult result = execCommandWithResult(targetHost, command);
        if (result.exitStatus != 0) {
            log.warn("远程命令返回非零退出码 [{}]: exit={}, cmd={}", targetHost, result.exitStatus, command);
        }
    }

    /**
     * 在远程网关上执行 Shell 命令，返回完整结果（含输出和退出码）
     */
    private CommandResult execCommandWithResult(String targetHost, String command) {
        log.info("SSH 远程命令 [{}] → {}", targetHost, command);
        Session session = null;
        ChannelExec exec = null;
        StringBuilder output = new StringBuilder();
        try {
            session = getSession(targetHost, null, null, null, null);
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(command);
            exec.setInputStream(null);

            java.io.InputStream in = exec.getInputStream();
            exec.connect(connectTimeout);

            // 读取命令输出
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                output.append(new String(buf, 0, len, StandardCharsets.UTF_8));
            }

            // 等待命令执行完成
            while (!exec.isClosed()) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }

            int exitStatus = exec.getExitStatus();
            log.info("SSH 命令执行完毕 [{}] exit={}", targetHost, exitStatus);
            return new CommandResult(exitStatus, output.toString().trim());
        } catch (Exception e) {
            log.error("SSH 命令执行失败 [{}]: {}", targetHost, command, e);
            throw new RuntimeException("无法在 Tinc 网关 [" + targetHost + "] 执行命令: " + e.getMessage(), e);
        } finally {
            if (exec != null && exec.isConnected()) exec.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    @Override
    public void deleteNetwork(String targetHost, String netName) {
        log.info("SSH 远程物理清理网络 [{}] → {}", netName, targetHost);
        // 1. 停止该网络的 tincd 进程（支持 systemd 和 直接命令双保险）
        String stopCmd = "systemctl stop tinc@" + netName + " 2>/dev/null; tincd -n " + netName + " -k 2>/dev/null || true";
        execCommand(targetHost, stopCmd);

        // 2. 物理删除该网络的配置文件夹
        String rmCmd = "rm -rf /etc/tinc/" + netName;
        execCommand(targetHost, rmCmd);
        log.info("SSH 远程网络物理删除成功 [{}] → {}", netName, targetHost);
    }

    @Override
    public void deleteNode(String targetHost, String netName, String nodeName) {
        log.info("SSH 远程删除节点 [{}] 属于网络 [{}] → {}", nodeName, netName, targetHost);
        // 1. 物理删除节点公钥 hosts 文件
        String rmCmd = "rm -f /etc/tinc/" + netName + "/hosts/" + nodeName;
        execCommand(targetHost, rmCmd);

        // 2. 重载网络，使节点断开并注销
        reloadTinc(targetHost, netName);
        log.info("SSH 远程节点删除并重载成功 [{}] → {}", nodeName, targetHost);
    }

    /**
     * SSH 命令执行结果
     */
    private static class CommandResult {
        final int exitStatus;
        final String output;

        CommandResult(int exitStatus, String output) {
            this.exitStatus = exitStatus;
            this.output = output;
        }
    }
}

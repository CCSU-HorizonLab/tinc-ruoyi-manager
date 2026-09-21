package com.ruoyi.common.utils;

import com.ruoyi.common.transport.TincConfigTransport;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import com.ruoyi.common.tinc.runtime.TincRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Tinc 配置文件生成工具（本地副本 + 远程推送双写版）
 * <p>
 * 每个 create* 方法同时执行两件事：
 * <ol>
 *   <li>写入 Java 进程所在机器的本地磁盘（用于调试、审计和客户端 API 下载读取）</li>
 *   <li>通过 TincConfigTransport 推送到远程 Tinc VPN 网关服务器（使 tincd 能加载最新配置）</li>
 * </ol>
 * </p>
 *
 * @author Sun
 */
public class TincConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(TincConfigUtils.class);

    /** 传输层 — 由 Spring TincTransportConfig 在启动时注入 */
    private static TincConfigTransport transport;
    private static TincRuntimeManager runtimeManager;

    /** 本地基路径：Linux /etc/tinc，Windows D:/tinc（用于本地副本写入和客户端下载读取） */
    private static volatile String localBasePath = System.getProperty("os.name").toLowerCase().startsWith("win")
            ? "D:/tinc"
            : "/etc/tinc";

    /**
     * 远程网关基路径：永远是 Linux 的 /etc/tinc
     * 无论管理后台运行在 Windows 还是 Linux，推送到网关的路径始终固定。
     * 这是 Bug 修复点：之前错误地用了 LOCAL_BASE_PATH（Windows 下为 D:/tinc）
     * 导致 SSH 推送路径变成 "D:/tinc/xxx"，Linux 网关无法识别。
     */
    private static final String REMOTE_BASE_PATH = "/etc/tinc";

    private static final Pattern TINC_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    private static final Object[] CLIENT_KEY_APPLY_LOCKS = createLocks(128);

    // ==================== Spring 注入入口 ====================

    /**
     * Spring 容器启动时由 TincTransportConfig 调用，注入传输层实现
     */
    public static void setTransport(TincConfigTransport t) {
        transport = t;
    }

    public static void setRuntimeManager(TincRuntimeManager manager) {
        runtimeManager = manager;
    }

    public static String getBasePath() {
        return localBasePath;
    }

    public static void setBasePath(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Tinc 配置根目录不能为空");
        }
        localBasePath = new File(basePath).toPath().toAbsolutePath().normalize().toString();
    }

    // ==================== 本地写入（保留副本，用于调试 + 客户端下载） ====================

    /**
     * 底层写入工具：写入 Java 进程所在机器的本地磁盘
     */
    private static void writeToFile(String netName, String fileName, String content, boolean isScript) {
        String fullPath = localBasePath + "/" + netName + "/" + fileName;
        File file = new File(fullPath);

        try {
            boolean changed = atomicWriteIfChanged(file.toPath(), content, isScript);
            log.info(changed ? "本地配置文件写入成功: {}" : "本地配置文件内容未变化，跳过替换: {}", fullPath);

        } catch (IOException e) {
            log.error("无法写入本地文件: {}", fullPath, e);
            throw new RuntimeException("生成配置失败: " + e.getMessage());
        }
    }

    /**
     * Writes a normalized UTF-8 file atomically only when its bytes changed. Keeping the
     * existing inode on an identical retry avoids observable mtime churn in local-gateway
     * mode, where the remote target and the controller's local copy are the same file.
     */
    static boolean atomicWriteIfChanged(java.nio.file.Path target, String content, boolean isScript)
            throws IOException {
        java.nio.file.Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("配置目标缺少父目录");
        }
        Files.createDirectories(parent);
        byte[] desired = content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        if (Files.isRegularFile(target) && MessageDigest.isEqual(Files.readAllBytes(target), desired)) {
            if (isScript && !System.getProperty("os.name").toLowerCase().startsWith("win")) {
                target.toFile().setExecutable(true, false);
            }
            return false;
        }

        java.nio.file.Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
        try {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(java.nio.ByteBuffer.wrap(desired));
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (isScript && !System.getProperty("os.name").toLowerCase().startsWith("win")) {
            target.toFile().setExecutable(true, false);
        }
        return true;
    }

    // ==================== 远程推送 ====================

    /**
     * 推送一个文件到远程网关（如果 transport 已配置且 gatewayIp 非空）
     * <p>
     * 注意：远程路径固定使用 REMOTE_BASE_PATH（/etc/tinc），与本机 OS 无关。
     * </p>
     */
    private static void pushToGateway(String gatewayIp, String netName, String fileName,
                                       String content, boolean executable) {
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            // ★ 关键修复：远程路径必须用 REMOTE_BASE_PATH（/etc/tinc），而非 LOCAL_BASE_PATH
            String remotePath = REMOTE_BASE_PATH + "/" + netName + "/" + fileName;
            transport.pushFile(gatewayIp, remotePath, content, executable);
        }
    }

    /**
     * 推送完成后重载远程网关的 tincd，使新配置生效
     */
    public static void reloadGatewayTinc(String gatewayIp, String netName) {
        requireTincIdentifier(netName, "网络名称");
        if (runtimeManager == null) throw new IllegalStateException("Tinc 运行管理器未配置");
        runtimeManager.reloadNetwork(netName);
    }

    // ==================== 公开 API（每个方法都做 本地 + 远程 双写） ====================

    public static void createTincConf(String gatewayIp, String netName, String nodeName, String connectToNode) {
        createTincConf(gatewayIp, netName, nodeName, connectToNode, null);
    }

    /**
     * 生成 tinc.conf（带端口）
     */
    public static void createTincConf(String gatewayIp, String netName, String nodeName, String connectToNode, String port) {
        createTincConf(gatewayIp, netName, nodeName, connectToNode, port, resolveInterfaceName(netName));
    }

    public static void createTincConf(String gatewayIp, String netName, String nodeName, String connectToNode,
                                      String port, String interfaceName) {
        requireTincIdentifier(netName, "网络名称");
        requireTincIdentifier(nodeName, "节点名称");
        if (interfaceName == null || !interfaceName.matches("[A-Za-z][A-Za-z0-9_.-]{0,14}")) {
            throw new IllegalArgumentException("接口名称无效");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Name = ").append(nodeName).append("\n");
        sb.append("Interface = ").append(interfaceName).append("\n");
        if (connectToNode != null && !connectToNode.trim().isEmpty()) {
            sb.append("ConnectTo = ").append(connectToNode).append("\n");
        }
        // 客户端配置包和网关统一采用三层路由模式，避免混用 switch/router。
        sb.append("Mode = router").append("\n");
        if (port != null && !port.trim().isEmpty()) {
            sb.append("Port = ").append(port).append("\n");
        }

        String content = sb.toString();
        writeToFile(netName, "tinc.conf", content, false);
        pushToGateway(gatewayIp, netName, "tinc.conf", content, false);
    }

    /**
     * 生成网卡启停脚本（tinc-up / tinc-down / tinc-up.bat / tinc-down.bat）
     *
     * @param gatewayIp 网关服务器公网 IP（本地模式可传 null）
     * @param netName   网络名称
     * @param virtualIp 虚拟 IP 地址（如 10.0.10.1）
     */
    public static void createTincUpAndDown(String gatewayIp, String netName, String virtualIp) {
        // Linux 启动脚本
        String linuxUp = "#!/bin/sh\n"
                + "/usr/sbin/ip address replace " + virtualIp + "/24 dev \"$INTERFACE\"\n"
                + "/usr/sbin/ip link set dev \"$INTERFACE\" up\n";
        writeToFile(netName, "tinc-up", linuxUp, true);
        pushToGateway(gatewayIp, netName, "tinc-up", linuxUp, true);

        // Linux 停止脚本
        String linuxDown = "#!/bin/sh\n/usr/sbin/ip link set dev \"$INTERFACE\" down\n";
        writeToFile(netName, "tinc-down", linuxDown, true);
        pushToGateway(gatewayIp, netName, "tinc-down", linuxDown, true);

        // Windows 启动脚本
        String winUp = "@echo off\r\nnetsh interface ipv4 set address name=\"%INTERFACE%\" source=static address="
                + virtualIp + " mask=255.255.255.0\r\n";
        writeToFile(netName, "tinc-up.bat", winUp, false);
        pushToGateway(gatewayIp, netName, "tinc-up.bat", winUp, false);

        // Windows 停止脚本
        String winDown = "@echo off\r\nnetsh interface ipv4 set address name=\"%INTERFACE%\" source=dhcp\r\n";
        writeToFile(netName, "tinc-down.bat", winDown, false);
        pushToGateway(gatewayIp, netName, "tinc-down.bat", winDown, false);
    }

    /**
     * 生成节点 Host 配置文件（不含公网 Address）
     *
     * @param gatewayIp 网关服务器公网 IP（本地模式可传 null）
     * @param netName   网络名称
     * @param nodeName  节点名称
     * @param subnet    子网地址（如 10.0.10.5/32）
     * @param publicKey PEM 格式公钥
     */
    public static void createHostFile(String gatewayIp, String netName, String nodeName,
                                       String subnet, String publicKey) {
        createHostFile(gatewayIp, netName, nodeName, subnet, null, null, publicKey);
    }

    public static void createHostFile(String gatewayIp, String netName, String nodeName,
                                       String subnet, String publicIp, String publicKey) {
        createHostFile(gatewayIp, netName, nodeName, subnet, publicIp, null, publicKey);
    }

    /**
     * 生成节点 Host 配置文件（含公网 Address 和 Port）
     */
    public static void createHostFile(String gatewayIp, String netName, String nodeName,
                                       String subnet, String publicIp, String port, String publicKey) {
        requireTincIdentifier(netName, "网络名称");
        requireTincIdentifier(nodeName, "节点名称");
        String content = buildHostFileContent(subnet, publicIp, port, publicKey);
        // 远程原子替换成功后才更新后台本地副本，SSH 失败时保留旧公钥供重试与下载。
        pushToGateway(gatewayIp, netName, "hosts/" + nodeName, content, false);
        writeToFile(netName, "hosts/" + nodeName, content, false);
    }

    public static String buildHostFileContent(String subnet, String publicIp, String port, String publicKey) {
        StringBuilder sb = new StringBuilder();
        if (publicIp != null && !publicIp.isEmpty()) {
            sb.append("Address = ").append(publicIp).append("\n");
        }
        if (port != null && !port.trim().isEmpty()) {
            sb.append("Port = ").append(port).append("\n");
        }
        sb.append("Subnet = ").append(subnet).append("\n\n");

        // 公钥自带完美格式，直接追加
        sb.append(publicKey);

        return sb.toString();
    }

    /**
     * 幂等应用客户端公钥。只有远程原子替换和 HUP 均成功后，才提交本地 host 与应用回执。
     * 同一 JVM 内相同节点的并发请求会串行化；仅有历史 host、但没有成功回执时不会误报幂等。
     *
     * @return true 表示本次执行了远程写入和重载；false 表示已确认是相同的已应用内容
     */
    public static boolean applyClientHostFileIfChanged(String gatewayIp, String netName, String nodeName,
                                                       String subnet, String publicKey) {
        requireTincIdentifier(netName, "网络名称");
        requireTincIdentifier(nodeName, "节点名称");
        if (transport == null || gatewayIp == null || gatewayIp.trim().isEmpty()) {
            throw new IllegalStateException("Tinc 网关传输未配置");
        }

        if (runtimeManager == null) {
            throw new IllegalStateException("Tinc 运行管理器未配置");
        }
        String content = buildHostFileContent(subnet, null, null, publicKey);
        String expectedHash = TincRuntimeSupport.sha256Normalized(content.getBytes(StandardCharsets.UTF_8));
        Object lock = CLIENT_KEY_APPLY_LOCKS[Math.floorMod(netName.hashCode(),
                CLIENT_KEY_APPLY_LOCKS.length)];
        synchronized (lock) {
            String remotePath = REMOTE_BASE_PATH + "/" + netName + "/hosts/" + nodeName;
            String previous = transport.readFile(gatewayIp, remotePath);
            if (normalizeConfig(content).equals(normalizeConfig(previous))) {
                runtimeManager.verifyPeerApplied(netName, nodeName, expectedHash);
                runtimeManager.ensureNetworkReady(netName);
                writeToFile(netName, "hosts/" + nodeName, content, false);
                writeToFile(netName, ".client-key-state/" + nodeName + ".sha256", expectedHash, false);
                return false;
            }

            try {
                pushToGateway(gatewayIp, netName, "hosts/" + nodeName, content, false);
                runtimeManager.reloadNetwork(netName);
                runtimeManager.verifyPeerApplied(netName, nodeName, expectedHash);
                runtimeManager.ensureNetworkReady(netName);
                writeToFile(netName, "hosts/" + nodeName, content, false);
                writeToFile(netName, ".client-key-state/" + nodeName + ".sha256", expectedHash, false);
                return true;
            } catch (RuntimeException applyFailure) {
                // Preserve the last valid peer file if HUP/readiness verification fails.
                if (previous != null) {
                    try {
                        transport.pushFile(gatewayIp, remotePath, previous, false);
                        runtimeManager.reloadNetwork(netName);
                    } catch (Exception restoreFailure) {
                        log.error("客户端 hosts 回滚失败: net={}, sid={}, errorType={}", netName, nodeName,
                                restoreFailure.getClass().getSimpleName());
                        applyFailure.addSuppressed(restoreFailure);
                    }
                }
                throw applyFailure;
            }
        }
    }

    /**
     * 撤销节点运行授权：删除 hosts 文件、HUP、确认文件已消失且网络仍 READY。
     * 失败时尽力恢复原文件；调用方只有在本方法成功后才可删除数据库记录。
     */
    public static void revokeClientHost(String gatewayIp, String netName, String nodeName) {
        requireTincIdentifier(netName, "网络名称");
        requireTincIdentifier(nodeName, "节点名称");
        if ("server_master".equals(nodeName)) {
            throw new IllegalArgumentException("不能撤销系统网关节点");
        }
        if (transport == null || runtimeManager == null) {
            throw new IllegalStateException("Tinc 网关传输或运行管理器未配置");
        }

        Object lock = CLIENT_KEY_APPLY_LOCKS[Math.floorMod(netName.hashCode(),
                CLIENT_KEY_APPLY_LOCKS.length)];
        synchronized (lock) {
            String remotePath = REMOTE_BASE_PATH + "/" + netName + "/hosts/" + nodeName;
            String previous = transport.readFile(gatewayIp, remotePath);
            if (previous == null) {
                runtimeManager.ensureNetworkReady(netName);
                deleteClientApplyReceipt(netName, nodeName);
                return;
            }
            try {
                transport.deleteNode(gatewayIp, netName, nodeName);
                if (transport.readFile(gatewayIp, remotePath) != null) {
                    throw new IllegalStateException("节点 hosts 文件撤销验证失败");
                }
                runtimeManager.reloadNetwork(netName);
                runtimeManager.ensureNetworkReady(netName);
                deleteClientApplyReceipt(netName, nodeName);
            } catch (RuntimeException revokeFailure) {
                try {
                    transport.pushFile(gatewayIp, remotePath, previous, false);
                    runtimeManager.reloadNetwork(netName);
                } catch (Exception restoreFailure) {
                    log.error("节点撤销回滚失败: net={}, sid={}, errorType={}", netName, nodeName,
                            restoreFailure.getClass().getSimpleName());
                    revokeFailure.addSuppressed(restoreFailure);
                }
                throw revokeFailure;
            }
        }
    }

    private static void deleteClientApplyReceipt(String netName, String nodeName) {
        try {
            Files.deleteIfExists(new File(localBasePath + "/" + netName
                    + "/.client-key-state/" + nodeName + ".sha256").toPath());
        } catch (IOException e) {
            throw new RuntimeException("节点应用回执清理失败", e);
        }
    }

    private static boolean hasMatchingClientApplyReceipt(String netName, String nodeName, String expectedContent) {
        String existingContent = readHostFile(netName, nodeName);
        if (!normalizeConfig(expectedContent).equals(normalizeConfig(existingContent))) {
            return false;
        }

        File receipt = new File(localBasePath + "/" + netName
                + "/.client-key-state/" + nodeName + ".sha256");
        if (!receipt.isFile()) {
            return false;
        }
        try {
            String expectedHash = sha256Hex(normalizeConfig(expectedContent));
            String actualHash = new String(Files.readAllBytes(receipt.toPath()), StandardCharsets.US_ASCII).trim();
            return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                    actualHash.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeConfig(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").trim();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static void requireTincIdentifier(String value, String field) {
        if (value == null || !TINC_IDENTIFIER.matcher(value).matches()
                || WINDOWS_RESERVED_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private static Object[] createLocks(int count) {
        Object[] locks = new Object[count];
        for (int i = 0; i < count; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    /** Existing networks keep their persisted interface; new networks get a stable unique candidate. */
    public static String resolveInterfaceName(String netName) {
        requireTincIdentifier(netName, "网络名称");
        File conf = new File(localBasePath + "/" + netName + "/tinc.conf");
        if (conf.isFile()) {
            try {
                for (String line : Files.readAllLines(conf.toPath(), StandardCharsets.UTF_8)) {
                    int equals = line.indexOf('=');
                    if (equals > 0 && "Interface".equals(line.substring(0, equals).trim())) {
                        String existing = line.substring(equals + 1).trim();
                        if (existing.matches("[A-Za-z][A-Za-z0-9_.-]{0,14}")) return existing;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("读取已分配 Tinc 接口失败", e);
            }
        }
        return "tn" + sha256Hex(netName).substring(0, 12);
    }

    /**
     * 写入 RSA 私钥文件
     *
     * @param gatewayIp 网关服务器公网 IP（本地模式可传 null）
     * @param netName   网络名称
     * @param privateKey PEM 格式私钥
     */
    public static void createPrivateKey(String gatewayIp, String netName, String privateKey) {
        writeToFile(netName, "rsa_key.priv", privateKey, false);
        pushToGateway(gatewayIp, netName, "rsa_key.priv", privateKey, false);
    }

    /**
     * 读取本地 Host 文件内容
     * <p>注意：这里始终读本地副本（不通过 SSH 远程读），因为客户端下载接口需要本地即可获取</p>
     */
    public static String readHostFile(String netName, String nodeName) {
        String fullPath = localBasePath + "/" + netName + "/hosts/" + nodeName;
        File file = new File(fullPath);
        if (!file.exists()) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败");
        }
    }

    /**
     * 初始化网络目录结构（本地），同时确保远程目录存在
     *
     * @param gatewayIp 网关服务器公网 IP（本地模式可传 null）
     * @param netName   网络名称
     */
    public static void initNetworkEnv(String gatewayIp, String netName) {
        String fullPath = localBasePath + "/" + netName;
        new File(fullPath).mkdirs();
        new File(fullPath + "/hosts").mkdirs();

        // 通过推送空占位文件触发远程 mkdirs（SshTincConfigTransport 已包含递归 mkdir 逻辑）
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            log.info("网络 [{}] 的远程网关目录结构已初始化", netName);
        }
    }

    /**
     * 物理删除本地备份配置并清理远程网关
     *
     * @param gatewayIp 网关服务器 IP
     * @param netName   网络名称
     */
    public static void deleteNetwork(String gatewayIp, String netName) {
        // 1. 删除本地备份配置文件夹
        String localDir = localBasePath + "/" + netName;
        File dir = new File(localDir);
        if (dir.exists()) {
            deleteLocalDir(dir);
            log.info("本地网络配置备份目录已删除: {}", localDir);
        }

        // 2. 调用传输层删除远程网关配置及停止进程
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            transport.deleteNetwork(gatewayIp, netName);
        }
    }

    /**
     * 物理删除本地节点配置并清理远程网关节点 hosts
     *
     * @param gatewayIp 网关服务器 IP
     * @param netName   网络名称
     * @param nodeName  节点名称
     */
    public static void deleteNode(String gatewayIp, String netName, String nodeName) {
        requireTincIdentifier(netName, "网络名称");
        requireTincIdentifier(nodeName, "节点名称");
        // 1. 删除本地备份节点配置文件
        String localFile = localBasePath + "/" + netName + "/hosts/" + nodeName;
        File file = new File(localFile);
        if (file.exists()) {
            file.delete();
            log.info("本地节点配置备份文件已删除: {}", localFile);
        }

        // 2. 调用传输层删除远程网关节点配置并重载
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            transport.deleteNode(gatewayIp, netName, nodeName);
        }
    }

    private static void deleteLocalDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteLocalDir(f);
                }
            }
        }
        dir.delete();
    }
}

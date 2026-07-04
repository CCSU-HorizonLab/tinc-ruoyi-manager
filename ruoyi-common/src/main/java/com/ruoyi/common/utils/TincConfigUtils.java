package com.ruoyi.common.utils;

import com.ruoyi.common.transport.TincConfigTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

    /** 本地基路径：Linux /etc/tinc，Windows D:/tinc（用于本地副本写入和客户端下载读取） */
    private static final String LOCAL_BASE_PATH = System.getProperty("os.name").toLowerCase().startsWith("win")
            ? "D:/tinc"
            : "/etc/tinc";

    /**
     * 远程网关基路径：永远是 Linux 的 /etc/tinc
     * 无论管理后台运行在 Windows 还是 Linux，推送到网关的路径始终固定。
     * 这是 Bug 修复点：之前错误地用了 LOCAL_BASE_PATH（Windows 下为 D:/tinc）
     * 导致 SSH 推送路径变成 "D:/tinc/xxx"，Linux 网关无法识别。
     */
    private static final String REMOTE_BASE_PATH = "/etc/tinc";

    // ==================== Spring 注入入口 ====================

    /**
     * Spring 容器启动时由 TincTransportConfig 调用，注入传输层实现
     */
    public static void setTransport(TincConfigTransport t) {
        transport = t;
    }

    public static String getBasePath() {
        return LOCAL_BASE_PATH;
    }

    // ==================== 本地写入（保留副本，用于调试 + 客户端下载） ====================

    /**
     * 底层写入工具：写入 Java 进程所在机器的本地磁盘
     */
    private static void writeToFile(String netName, String fileName, String content, boolean isScript) {
        String fullPath = LOCAL_BASE_PATH + "/" + netName + "/" + fileName;
        File file = new File(fullPath);

        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            // 剔除所有 \r，统一使用 Unix 换行
            String safeContent = content.replace("\r\n", "\n");

            // 使用 NIO 写入纯正字节流
            Files.write(file.toPath(), safeContent.getBytes(StandardCharsets.UTF_8));

            // 如果是脚本且在 Linux 环境下，赋予执行权限
            if (isScript && !System.getProperty("os.name").toLowerCase().startsWith("win")) {
                file.setExecutable(true, false);
            }
            log.info("本地配置文件写入成功: {}", fullPath);

        } catch (IOException e) {
            log.error("无法写入本地文件: {}", fullPath, e);
            throw new RuntimeException("生成配置失败: " + e.getMessage());
        }
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
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            transport.reloadTinc(gatewayIp, netName);
        }
    }

    // ==================== 公开 API（每个方法都做 本地 + 远程 双写） ====================

    public static void createTincConf(String gatewayIp, String netName, String nodeName, String connectToNode) {
        createTincConf(gatewayIp, netName, nodeName, connectToNode, null);
    }

    /**
     * 生成 tinc.conf（带端口）
     */
    public static void createTincConf(String gatewayIp, String netName, String nodeName, String connectToNode, String port) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name = ").append(nodeName).append("\n");
        sb.append("Interface = tinc0").append("\n");
        if (connectToNode != null && !connectToNode.trim().isEmpty()) {
            sb.append("ConnectTo = ").append(connectToNode).append("\n");
        }
        sb.append("Mode = switch").append("\n");
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
        String linuxUp = "#!/bin/sh\nifconfig $INTERFACE " + virtualIp + " netmask 255.255.255.0\n";
        writeToFile(netName, "tinc-up", linuxUp, true);
        pushToGateway(gatewayIp, netName, "tinc-up", linuxUp, true);

        // Linux 停止脚本
        String linuxDown = "#!/bin/sh\nifconfig $INTERFACE down\n";
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

        String content = sb.toString();
        writeToFile(netName, "hosts/" + nodeName, content, false);
        pushToGateway(gatewayIp, netName, "hosts/" + nodeName, content, false);
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
        String fullPath = LOCAL_BASE_PATH + "/" + netName + "/hosts/" + nodeName;
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
        String fullPath = LOCAL_BASE_PATH + "/" + netName;
        new File(fullPath).mkdirs();
        new File(fullPath + "/hosts").mkdirs();

        // 通过推送空占位文件触发远程 mkdirs（SshTincConfigTransport 已包含递归 mkdir 逻辑）
        if (transport != null && gatewayIp != null && !gatewayIp.isEmpty()) {
            log.info("网络 [{}] 目录结构已初始化，远程网关: {}", netName, gatewayIp);
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
        String localDir = LOCAL_BASE_PATH + "/" + netName;
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
        // 1. 删除本地备份节点配置文件
        String localFile = LOCAL_BASE_PATH + "/" + netName + "/hosts/" + nodeName;
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

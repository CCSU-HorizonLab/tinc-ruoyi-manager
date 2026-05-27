package com.ruoyi.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tinc 配置文件生成工具 (纯净 NIO 底层版)
 * @author Sun
 */
public class TincConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(TincConfigUtils.class);

    // 智能路径选择：开发环境(Win)用 D盘，生产环境(Linux)用 /etc
    private static final String BASE_PATH = System.getProperty("os.name").toLowerCase().startsWith("win")
            ? "D:/tinc"
            : "/etc/tinc";

    public static void createTincConf(String netName, String nodeName, String connectToNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name = ").append(nodeName).append("\n");
        sb.append("Interface = tinc0").append("\n");
        // 🚨 架构师防呆设计：只有当明确指定了目标时，才生成主动拨号指令
        // 中心服务器调用时，传 null 或 "" 即可，它将只作为安静的监听者
        if (connectToNode != null && !connectToNode.trim().isEmpty()) {
            sb.append("ConnectTo = ").append(connectToNode).append("\n");
        }
        sb.append("Mode = switch").append("\n");

        writeToFile(netName, "tinc.conf", sb.toString(), false);
    }

    public static void createTincUpAndDown(String netName, String virtualIp) {
        // Linux 启动脚本
        String linuxUp = "#!/bin/sh\nifconfig $INTERFACE " + virtualIp + " netmask 255.255.255.0\n";
        writeToFile(netName, "tinc-up", linuxUp, true);

        // Linux 停止脚本
        String linuxDown = "#!/bin/sh\nifconfig $INTERFACE down\n";
        writeToFile(netName, "tinc-down", linuxDown, true);

        // Windows 启动脚本
        String winUp = "@echo off\r\nnetsh interface ipv4 set address name=\"%INTERFACE%\" source=static address=" + virtualIp + " mask=255.255.255.0\r\n";
        writeToFile(netName, "tinc-up.bat", winUp, false);

        // Windows 停止脚本
        String winDown = "@echo off\r\nnetsh interface ipv4 set address name=\"%INTERFACE%\" source=dhcp\r\n";
        writeToFile(netName, "tinc-down.bat", winDown, false);
    }

    public static void createHostFile(String netName, String nodeName, String subnet, String publicKey) {
        createHostFile(netName, nodeName, subnet, null, publicKey);
    }

    public static void createHostFile(String netName, String nodeName, String subnet, String publicIp, String publicKey) {
        StringBuilder sb = new StringBuilder();
        if (publicIp != null && !publicIp.isEmpty()) {
            sb.append("Address = ").append(publicIp).append("\n");
        }
        sb.append("Subnet = ").append(subnet).append("\n\n");

        // 公钥自带完美格式，直接追加
        sb.append(publicKey);

        writeToFile(netName, "hosts/" + nodeName, sb.toString(), false);
    }

    public static void createPrivateKey(String netName, String privateKey) {
        // 私钥自带完美格式，直接写入
        writeToFile(netName, "rsa_key.priv", privateKey, false);
    }

    /**
     * 底层写入工具 (彻底消灭 Windows CRLF，统一使用 UTF-8 字节流)
     */
    private static void writeToFile(String netName, String fileName, String content, boolean isScript) {
        String fullPath = BASE_PATH + "/" + netName + "/" + fileName;
        File file = new File(fullPath);

        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            // 🚨 终极防线：无论传进来的字符串包含什么，写入硬盘前强行剔除所有 \r
            String safeContent = content.replace("\r\n", "\n");

            // 抛弃带有 Windows 基因的 FileWriter！使用 NIO 写入纯正字节流
            Files.write(file.toPath(), safeContent.getBytes(StandardCharsets.UTF_8));

            // 如果是脚本且在 Linux 环境下，赋予执行权限
            if (isScript && !System.getProperty("os.name").toLowerCase().startsWith("win")) {
                file.setExecutable(true, false);
            }
            log.info("配置文件生成成功: {}", fullPath);

        } catch (IOException e) {
            log.error("无法写入文件: {}", fullPath, e);
            throw new RuntimeException("生成配置失败: " + e.getMessage());
        }
    }

    public static String readHostFile(String netName, String nodeName) {
        String fullPath = BASE_PATH + "/" + netName + "/hosts/" + nodeName;
        File file = new File(fullPath);
        if (!file.exists()) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败");
        }
    }

    public static void initNetworkEnv(String netName) {
        String fullPath = BASE_PATH + "/" + netName;
        new File(fullPath).mkdirs();
        new File(fullPath + "/hosts").mkdirs();
    }

    public static String getBasePath() {
        return BASE_PATH;
    }
}
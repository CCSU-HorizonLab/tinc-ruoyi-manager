package com.ruoyi.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


/**
 * Tinc 配置文件生成工具 (修复版 - 强制 PEM 格式 + 自动填充 Subnet)
 * @author Sun & Gemini Coach
 */
public class TincConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(TincConfigUtils.class);

    // 智能路径选择：开发环境(Win)用 D盘，生产环境(Linux)用 /etc
    private static final String BASE_PATH = System.getProperty("os.name").toLowerCase().startsWith("win")
            ? "D:/tinc"
            : "/etc/tinc";

    /**
     * 生成 tinc.conf (保持不变)
     */
    public static void createTincConf(String netName, String nodeName, String connectToNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name = ").append(nodeName).append("\n");
        sb.append("Interface = tinc0").append("\n");
        sb.append("ConnectTo = ").append(connectToNode).append("\n"); // Client 必须要连 Server
        sb.append("Mode = switch").append("\n");

        writeToFile(netName, "tinc.conf", sb.toString());
    }

    // ==========================================
    // 核心修复：生成标准 hosts 文件
    // ==========================================

    /**
     * 版本 A: Client 节点专用 (不需要 Address，但必须有 Subnet)
     * 例如调用: createHostFile("segment", "sun666", "192.172.100.3/32", publicKey);
     */
    public static void createHostFile(String netName, String nodeName, String subnet, String publicKey) {
        // 自动调用版本 B，把 publicIp 传为 null，这样就不会生成 Address 字段
        createHostFile(netName, nodeName, subnet, null, publicKey);
    }

    /**
     * 版本 B: 通用版 / Server 节点 (需要 Address 和 Subnet)
     */
    public static void createHostFile(String netName, String nodeName, String subnet, String publicIp, String publicKey) {
        StringBuilder sb = new StringBuilder();

        // 1. [关键] 写入 Address (仅当 publicIp 存在时，如服务端)
        if (publicIp != null && !publicIp.isEmpty()) {
            sb.append("Address = ").append(publicIp).append("\n");
        }

        // 2. [关键] 写入 Subnet (你刚才要求的字段，这里一定会写进去！)
        // 例如: Subnet = 192.172.100.3/32
        sb.append("Subnet = ").append(subnet).append("\n");

        sb.append("\n"); // 空一行，保持美观

        // 3. [关键] 写入公钥 (强制换行，修复 PEM 格式问题)
        // Java 默认生成 PKCS#8 格式，标准头应该是 BEGIN PRIVATE KEY 而不是tinc的-----BEGIN RSA PUBLIC KEY-----
        sb.append("-----BEGIN  PUBLIC KEY-----\n");
        sb.append(formatToMime(publicKey)); // <--- 这里的函数会负责切分换行
        sb.append("-----END  PUBLIC KEY-----\n");

        writeToFile(netName, "hosts/" + nodeName, sb.toString());
    }

    /**
     * 生成私钥文件 (rsa_key.priv)
     */
    public static void createPrivateKey(String netName, String privateKey) {
        StringBuilder sb = new StringBuilder();
        // Java 默认生成 PKCS#8 格式，标准头应该是 BEGIN PRIVATE KEY 而不是tinc的-----BEGIN RSA PUBLIC KEY-----
        sb.append("-----BEGIN  PRIVATE KEY-----\n");
        sb.append(formatToMime(privateKey));
        sb.append("-----END  PRIVATE KEY-----\n");

        writeToFile(netName, "rsa_key.priv", sb.toString());
    }

    // ==========================================
    // 新增：密钥对生成工具
    // ==========================================

    /**
     * 随机生成一对 RSA 密钥 (4096位)
     */
    public static Map<String, String> generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(4096);
            KeyPair pair = keyGen.generateKeyPair();

            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

            Map<String, String> keys = new HashMap<>();
            keys.put("private", privateKey);
            keys.put("public", publicKey);
            return keys;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("生成密钥失败: " + e.getMessage());
        }
    }

    // ==========================================
    // 内部工具方法
    // ==========================================

    /**
     * [关键] 将 Base64 字符串格式化为标准 MIME 格式 (每行64字符+\n)
     */
    private static String formatToMime(String rawBase64) {
        if (rawBase64 == null) return "";
        if (rawBase64.contains("BEGIN RSA")) return rawBase64;

        byte[] decode = Base64.getDecoder().decode(rawBase64);
        return Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(decode) + "\n";
    }

    private static void writeToFile(String netName, String fileName, String content) {
        String fullPath = BASE_PATH + "/" + netName + "/" + fileName;
        File file = new File(fullPath);

        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
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
        if (!file.exists()) {
            log.warn("文件不存在: {}", fullPath);
            return null;
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败");
        }
    }

    public static String getBasePath() {
        return BASE_PATH;
    }
    // 在 TincConfigUtils.java 中添加这个方法
    public static void initNetworkEnv(String netName) {
        // 自动判断系统路径
        String basePath = (System.getProperty("os.name").toLowerCase().startsWith("win") ? "D:/tinc" : "/etc/tinc") + "/" + netName;

        // 创建 3 个关键目录
        new File(basePath).mkdirs();          // 根目录
        new File(basePath + "/hosts").mkdirs();   // 公钥池
        new File(basePath + "/clients").mkdirs(); // 打包临时仓
    }
}
package com.ruoyi.common.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * 本地文件传输实现（向后兼容单机部署场景）
 * <p>
 * 直接将配置文件写入 Java 进程所在机器的本地磁盘。
 * 适用于管理后台和 Tinc VPN 网关运行在同一台物理机上的场景。
 * </p>
 *
 * @author Sun
 * @date 2026-07-03
 */
public class LocalTincConfigTransport implements TincConfigTransport {

    private static final Logger log = LoggerFactory.getLogger(LocalTincConfigTransport.class);

    private final String basePath;

    public LocalTincConfigTransport(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void pushFile(String targetHost, String remotePath, String content, boolean executable) {
        // 本地模式下忽略 targetHost，直接写本地路径
        File file = new File(remotePath);
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            String safeContent = content.replace("\r\n", "\n");
            Files.write(file.toPath(), safeContent.getBytes(StandardCharsets.UTF_8));

            if (executable && !System.getProperty("os.name").toLowerCase().startsWith("win")) {
                file.setExecutable(true, false);
            }
            log.info("本地配置写入成功: {}", remotePath);
        } catch (IOException e) {
            log.error("本地配置写入失败: {}", remotePath, e);
            throw new RuntimeException("生成配置失败: " + e.getMessage(), e);
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
        log.info("本地模式: 跳过远程 tincd 重载。如需手动重载: tincd -n {} -kHUP", netName);
    }

    @Override
    public void restartTinc(String targetHost, String netName) {
        log.info("本地模式: 跳过远程 tincd 重启。如需手动重启: systemctl restart tinc@{}", netName);
    }

    @Override
    public boolean isTincInstalled(String targetHost) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", "tincd"});
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            log.warn("本地检测 tincd 失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String installTinc(String targetHost) {
        log.info("本地模式: 请手动安装 tinc。");
        log.info("  Ubuntu/Debian: apt-get install tinc");
        log.info("  CentOS/RHEL:   yum install epel-release && yum install tinc");
        return "本地模式不支持自动安装，请手动执行上述命令安装 tinc。";
    }

    @Override
    public void deleteNetwork(String targetHost, String netName) {
        log.info("本地模式: 开始物理清理网络目录 {}/{}", basePath, netName);
        File dir = new File(basePath + "/" + netName);
        deleteDir(dir);

        // 停止本地特定网络的 tincd 进程
        try {
            Process p;
            if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
                // Windows 下调用 tincd -n <netName> -k 停止服务
                p = Runtime.getRuntime().exec(new String[]{"tincd.exe", "-n", netName, "-k"});
            } else {
                p = Runtime.getRuntime().exec(new String[]{"tincd", "-n", netName, "-k"});
            }
            p.waitFor();
        } catch (Exception e) {
            log.warn("本地停止 tincd [{}] 失败: {}", netName, e.getMessage());
        }
    }

    @Override
    public void deleteNode(String targetHost, String netName, String nodeName) {
        log.info("本地模式: 开始删除节点配置 {}/{}/hosts/{}", basePath, netName, nodeName);
        File file = new File(basePath + "/" + netName + "/hosts/" + nodeName);
        if (file.exists()) {
            file.delete();
        }
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDir(f);
                }
            }
        }
        dir.delete();
    }
}

package com.ruoyi.common.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
    private final Path configuredRoot;
    private final Path baseRoot;
    private final String fileOwner;
    private final String fileGroup;
    private final TincRuntimeManager runtimeManager;
    private final ConcurrentHashMap<Path, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public LocalTincConfigTransport(String basePath) {
        this(basePath, null, null, null);
    }

    public LocalTincConfigTransport(String basePath, String fileOwner, String fileGroup) {
        this(basePath, fileOwner, fileGroup, null);
    }

    public LocalTincConfigTransport(String basePath, String fileOwner, String fileGroup,
                                    TincRuntimeManager runtimeManager) {
        this.basePath = basePath;
        Path normalizedRoot = new File(basePath).toPath().toAbsolutePath().normalize();
        this.configuredRoot = normalizedRoot;
        try {
            if (Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(normalizedRoot)) {
                    throw new IllegalArgumentException("Tinc 配置根目录不能是符号链接");
                }
                normalizedRoot = normalizedRoot.toRealPath();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析 Tinc 配置根目录", e);
        }
        this.baseRoot = normalizedRoot;
        this.fileOwner = fileOwner;
        this.fileGroup = fileGroup;
        this.runtimeManager = runtimeManager;
    }

    @Override
    public void pushFile(String targetHost, String remotePath, String content, boolean executable) {
        // 本地模式下忽略 targetHost，直接写本地路径
        File file = safePath(remotePath).toFile();
        Path normalizedTarget = file.toPath().toAbsolutePath().normalize();
        ReentrantLock fileLock = fileLocks.computeIfAbsent(normalizedTarget, ignored -> new ReentrantLock());
        fileLock.lock();
        try {
            Files.createDirectories(normalizedTarget.getParent());
            ensureResolvedWithinRoot(normalizedTarget.getParent());
            String safeContent = content.replace("\r\n", "\n");
            Path target = file.toPath();
            Path temporary = Files.createTempFile(target.getParent(), ".tinc-write-", ".tmp");
            try {
                byte[] bytes = safeContent.getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
                if (!System.getProperty("os.name").toLowerCase().startsWith("win")) {
                    Files.setPosixFilePermissions(temporary, executable
                            ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE)
                            : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                    applyOwnership(temporary);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                forceDirectory(target.getParent());
            } finally {
                Files.deleteIfExists(temporary);
            }
            log.info("本地配置原子写入成功: pathType={}", executable ? "script" : "config");
        } catch (IOException e) {
            log.error("本地配置原子写入失败，错误类型={}", e.getClass().getSimpleName());
            throw new RuntimeException("生成配置失败", e);
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public String readFile(String targetHost, String remotePath) {
        try {
            Path path = safePath(remotePath);
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            throw new RuntimeException("读取 Tinc 配置失败", e);
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
        if (runtimeManager == null) throw new IllegalStateException("Tinc 运行管理器未配置");
        runtimeManager.reloadNetwork(netName);
    }

    @Override
    public void restartTinc(String targetHost, String netName) {
        if (runtimeManager == null) throw new IllegalStateException("Tinc 运行管理器未配置");
        // Legacy call sites are deliberately converted to non-disruptive ensure semantics.
        runtimeManager.ensureNetworkReady(netName);
    }

    @Override
    public boolean isTincInstalled(String targetHost) {
        return Files.isExecutable(new File("/usr/sbin/tincd").toPath());
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

    private void forceDirectory(Path directory) {
        if (System.getProperty("os.name").toLowerCase().startsWith("win")) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (Exception e) {
            log.debug("目录 fsync 不受当前文件系统支持");
        }
    }

    private Path safePath(String path) {
        Path candidate = new File(path).toPath().toAbsolutePath().normalize();
        if (!candidate.startsWith(configuredRoot) || Files.isSymbolicLink(candidate)) {
            throw new IllegalArgumentException("Tinc path is outside the configured root");
        }
        ensureResolvedWithinRoot(nearestExistingPath(candidate));
        return candidate;
    }

    private Path nearestExistingPath(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalArgumentException("Tinc path has no existing parent");
        }
        return current;
    }

    private void ensureResolvedWithinRoot(Path path) {
        try {
            if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(baseRoot)) {
                throw new IllegalArgumentException("Tinc path is outside the configured root");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve Tinc path", e);
        }
    }

    private void applyOwnership(Path path) throws IOException {
        if (fileOwner != null && !fileOwner.trim().isEmpty()) {
            UserPrincipal owner = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(fileOwner.trim());
            Files.setOwner(path, owner);
        }
        if (fileGroup != null && !fileGroup.trim().isEmpty()) {
            GroupPrincipal group = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByGroupName(fileGroup.trim());
            Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(group);
        }
    }
}

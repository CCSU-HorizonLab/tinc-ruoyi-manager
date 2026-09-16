package com.ruoyi.common.tinc.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the local systemd data plane. Every mutating operation is serialized per network.
 * Commands are tokenized and executed through an executable allow-list.
 */
@Service
public class LocalTincRuntimeManager implements TincRuntimeManager {
    private static final Logger log = LoggerFactory.getLogger(LocalTincRuntimeManager.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    private static final Pattern INTERFACE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,14}");
    private static final Pattern IPV4 = Pattern.compile("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");
    private static final Pattern INET_OUTPUT = Pattern.compile("\\binet\\s+(" + IPV4.pattern() + ")/\\d{1,2}\\b");
    private static final Set<PosixFilePermission> PRIVATE_MODE = Collections.unmodifiableSet(EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    private static final Set<PosixFilePermission> SCRIPT_MODE = Collections.unmodifiableSet(EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> reloadResults = new ConcurrentHashMap<>();
    private final TincCommandExecutor executor;
    private final Path configRoot;
    private final Duration commandTimeout;
    private final Duration readyTimeout;

    @Autowired
    public LocalTincRuntimeManager(
            @Value("${tinc.runtime.config-root:/etc/tinc}") String configRoot,
            @Value("${tinc.runtime.command-timeout-ms:10000}") long commandTimeoutMs,
            @Value("${tinc.runtime.ready-timeout-ms:15000}") long readyTimeoutMs,
            @Value("${tinc.runtime.use-helper:false}") boolean useHelper,
            @Value("${tinc.runtime.helper-use-sudo:false}") boolean helperUseSudo) {
        this(useHelper ? new HelperTincCommandExecutor(helperUseSudo) : new LocalTincCommandExecutor(), Paths.get(configRoot),
                Duration.ofMillis(commandTimeoutMs), Duration.ofMillis(readyTimeoutMs));
    }

    /** Visible for deterministic unit tests. */
    public LocalTincRuntimeManager(TincCommandExecutor executor, Path configRoot,
                                   Duration commandTimeout, Duration readyTimeout) {
        this.executor = executor;
        Path normalizedRoot = configRoot.toAbsolutePath().normalize();
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
        this.configRoot = normalizedRoot;
        this.commandTimeout = commandTimeout;
        this.readyTimeout = readyTimeout;
    }

    @Override
    public TincNetworkStatus validateNetworkConfig(String netName) {
        requireIdentifier(netName, "网络名称");
        TincNetworkStatus status = new TincNetworkStatus();
        status.setNetName(netName);
        try {
            Config config = loadConfig(netName);
            status.setInterfaceName(config.interfaceName);
            status.setPort(config.port);
            status.setExpectedVpnIp(config.expectedVpnIp);
            status.setPrivateKeyPresent(config.privateKeyPresent);
            status.setConfigValid(true);
            status.setReadiness("VALIDATED");
        } catch (TincRuntimeException e) {
            status.fail(e.getCode(), e.getMessage());
        }
        return status;
    }

    @Override
    public TincNetworkStatus ensureNetworkReady(String netName) {
        requireIdentifier(netName, "网络名称");
        ReentrantLock lock = locks.computeIfAbsent(netName, key -> new ReentrantLock());
        lock.lock();
        try {
            fixRequiredPermissions(netName);
            Config config = loadConfig(netName);
            detectInterfaceAndPortConflicts(netName);
            ensureFirewallPort(config.port);

            TincNetworkStatus before = inspectNetworkStatus(netName);
            if (!before.isSystemdActive() || !before.isMainPidPresent()) {
                startAndEnableNetworkUnlocked(netName);
            }

            long deadline = System.nanoTime() + readyTimeout.toNanos();
            TincNetworkStatus latest;
            do {
                latest = inspectNetworkStatus(netName);
                if (latest.isReady()) {
                    return latest;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY,
                            "等待 Tinc 网络就绪时被中断", e);
                }
            } while (System.nanoTime() < deadline);

            // A timed-out start may still have completed. Re-read once before deciding.
            latest = inspectNetworkStatus(netName);
            if (latest.isReady()) {
                return latest;
            }
            throw fromStatus(latest);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void startAndEnableNetwork(String netName) {
        requireIdentifier(netName, "网络名称");
        ReentrantLock lock = locks.computeIfAbsent(netName, key -> new ReentrantLock());
        lock.lock();
        try {
            fixRequiredPermissions(netName);
            loadConfig(netName);
            detectInterfaceAndPortConflicts(netName);
            startAndEnableNetworkUnlocked(netName);
        } finally {
            lock.unlock();
        }
    }

    private void startAndEnableNetworkUnlocked(String netName) {
        TincCommandResult result = run("/usr/bin/systemctl", "enable", "--now", unit(netName));
        if (!result.isSuccess()) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_SERVICE_START_FAILED,
                    "Tinc 服务启动失败，请管理员检查 systemd 日志");
        }
    }

    @Override
    public void reloadNetwork(String netName) {
        requireIdentifier(netName, "网络名称");
        ReentrantLock lock = locks.computeIfAbsent(netName, key -> new ReentrantLock());
        lock.lock();
        try {
            TincNetworkStatus before = inspectNetworkStatus(netName);
            if (!before.isSystemdActive() || !before.isMainPidPresent()) {
                ensureNetworkReady(netName);
                reloadResults.put(netName, "STARTED");
                return;
            }
            TincCommandResult result = run("/usr/sbin/tincd", "-n", netName, "-kHUP");
            if (!result.isSuccess()) {
                reloadResults.put(netName, "FAILED");
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RELOAD_FAILED,
                        "Tinc 公钥重载失败，未重启网络以避免中断其他用户");
            }
            TincNetworkStatus after = inspectNetworkStatus(netName);
            if (!after.isSystemdActive() || !after.isMainPidPresent()) {
                reloadResults.put(netName, "FAILED_DAEMON_EXITED");
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RELOAD_FAILED,
                        "Tinc 重载后进程未保持运行");
            }
            reloadResults.put(netName, "HUP_OK");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TincNetworkStatus inspectNetworkStatus(String netName) {
        requireIdentifier(netName, "网络名称");
        TincNetworkStatus status = validateNetworkConfig(netName);
        status.setLastReloadResult(reloadResults.getOrDefault(netName, "NOT_RELOADED"));
        if (!status.isConfigValid()) {
            return status;
        }
        try {
            detectInterfaceAndPortConflicts(netName);
            status.setSystemdEnabled(success("/usr/bin/systemctl", "is-enabled", unit(netName)));
            status.setSystemdActive(success("/usr/bin/systemctl", "is-active", unit(netName)));
            TincCommandResult pid = run("/usr/bin/systemctl", "show", unit(netName),
                    "--property", "MainPID", "--value");
            status.setMainPidPresent(pid.isSuccess() && parsePositiveLong(pid.getOutput()) > 0);

            TincCommandResult link = run("/usr/sbin/ip", "link", "show", "dev", status.getInterfaceName());
            status.setInterfacePresent(link.isSuccess());
            if (status.isInterfacePresent()) {
                TincCommandResult addr = run("/usr/sbin/ip", "-4", "-o", "addr", "show", "dev",
                        status.getInterfaceName());
                Matcher matcher = INET_OUTPUT.matcher(addr.getOutput());
                if (matcher.find()) {
                    status.setActualVpnIp(matcher.group(1));
                }
            }
            status.setTcpListening(isListening("-ltnp", status.getPort()));
            status.setUdpListening(isListening("-lunp", status.getPort()));

            if (!status.isSystemdActive()) {
                status.fail(TincRuntimeErrorCode.TINC_SERVICE_INACTIVE, "Tinc 服务未运行");
            } else if (!status.isMainPidPresent()) {
                status.fail(TincRuntimeErrorCode.TINC_SERVICE_INACTIVE, "Tinc 服务没有有效主进程");
            } else if (!status.isInterfacePresent() || !status.getExpectedVpnIp().equals(status.getActualVpnIp())) {
                status.fail(TincRuntimeErrorCode.TINC_INTERFACE_NOT_READY, "Tinc 接口或服务端 VPN 地址未就绪");
            } else if (!status.isTcpListening()) {
                status.fail(TincRuntimeErrorCode.TINC_TCP_NOT_LISTENING, "Tinc TCP 端口未监听");
            } else if (!status.isUdpListening()) {
                status.fail(TincRuntimeErrorCode.TINC_UDP_NOT_LISTENING, "Tinc UDP 端口未监听");
            } else {
                status.setReadiness("READY");
                status.setFailureCode(null);
                status.setFailureMessage(null);
            }
        } catch (TincRuntimeException e) {
            status.fail(e.getCode(), e.getMessage());
        }
        return status;
    }

    @Override
    public void verifyPeerApplied(String netName, String sid, String expectedSha256) {
        requireIdentifier(netName, "网络名称");
        requireIdentifier(sid, "节点名称");
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("公钥配置指纹无效");
        }
        Path host = networkPath(netName).resolve("hosts").resolve(sid).normalize();
        requireWithinNetwork(netName, host);
        try {
            if (!isSafeRegularFile(host)) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY,
                        "客户端 hosts 文件不存在");
            }
            String actual = TincRuntimeSupport.sha256Normalized(Files.readAllBytes(host));
            if (!constantTimeEquals(expectedSha256, actual)) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY,
                        "客户端 hosts 文件指纹校验失败");
            }
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY,
                    "无法校验客户端 hosts 文件", e);
        }
    }

    @Override
    public void ensureFirewallPort(int port) {
        requirePort(port);
        TincCommandResult state = run("/usr/bin/firewall-cmd", "--state");
        if (!state.isSuccess() || !"running".equals(state.getOutput().trim())) {
            String backend = detectPacketFilterBackend();
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_FIREWALL_FAILED,
                    "firewalld 未运行，检测到 " + backend + "；当前版本不支持自动修改该防火墙，请管理员同时放行 TCP/UDP " + port);
        }
        ensureFirewalldRule(port, "tcp", false);
        ensureFirewalldRule(port, "udp", false);
        ensureFirewalldRule(port, "tcp", true);
        ensureFirewalldRule(port, "udp", true);
    }

    @Override
    public void detectInterfaceAndPortConflicts(String netName) {
        requireIdentifier(netName, "网络名称");
        Config own = loadConfig(netName);
        if (!Files.isDirectory(configRoot)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(configRoot)) {
            for (Path entry : entries) {
                String otherName = entry.getFileName().toString();
                if (otherName.equals(netName) || !IDENTIFIER.matcher(otherName).matches()) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)) {
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                            "Tinc 配置根目录包含不安全的网络符号链接");
                }
                Path conf = entry.resolve("tinc.conf");
                if (!Files.isRegularFile(conf, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Map<String, String> values = readAssignments(conf);
                String otherInterface = values.get("Interface");
                Integer otherPort = parsePortOrNull(values.get("Port"));
                if (own.interfaceName.equals(otherInterface)) {
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_INTERFACE_CONFLICT,
                            "接口 " + own.interfaceName + " 已分配给其他 Tinc 网络 " + redactName(otherName)
                                    + "；请由管理员迁移或停用冲突网络");
                }
                if (otherPort != null && own.port == otherPort) {
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_PORT_CONFLICT,
                            "端口 " + own.port + " 已分配给其他 Tinc 网络 " + redactName(otherName)
                                    + "；请由管理员更换端口");
                }
            }
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "无法检查 Tinc 接口和端口分配", e);
        }

        TincCommandResult ownPid = run("/usr/bin/systemctl", "show", unit(netName),
                "--property", "MainPID", "--value");
        long pid = ownPid.isSuccess() ? parsePositiveLong(ownPid.getOutput()) : 0;
        checkSocketOwner(own.port, "-ltnp", pid);
        checkSocketOwner(own.port, "-lunp", pid);
    }

    private Config loadConfig(String netName) {
        Path network = networkPath(netName);
        Path conf = network.resolve("tinc.conf");
        Path serverHost = network.resolve("hosts").resolve("server_master");
        Path privateKey = network.resolve("rsa_key.priv");
        if (!isSafeRegularFile(conf) || !isSafeRegularFile(serverHost)) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "Tinc 主配置或 server_master 主机配置缺失");
        }
        if (!isSafeRegularFile(privateKey)) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_PRIVATE_KEY_MISSING,
                    "Tinc 服务端私钥缺失，请管理员恢复原私钥");
        }
        try {
            if (Files.size(privateKey) <= 0) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_PRIVATE_KEY_MISSING,
                        "Tinc 服务端私钥为空，请管理员恢复原私钥");
            }
            ensureScriptValid(network.resolve("tinc-up"));
            ensureScriptValid(network.resolve("tinc-down"));
            Map<String, String> main = readAssignments(conf);
            Map<String, String> host = readAssignments(serverHost);
            String hostContent = new String(Files.readAllBytes(serverHost), StandardCharsets.UTF_8);
            String name = main.get("Name");
            String interfaceName = main.get("Interface");
            String mode = main.get("Mode");
            Integer port = parsePortOrNull(main.get("Port"));
            Integer hostPort = parsePortOrNull(host.get("Port"));
            String subnet = host.get("Subnet");
            if (!"server_master".equals(name) || !"router".equalsIgnoreCase(mode)
                    || interfaceName == null || !INTERFACE.matcher(interfaceName).matches()
                    || port == null || hostPort == null || !port.equals(hostPort)
                    || subnet == null || !subnet.endsWith("/32")
                    || !hostContent.contains("-----BEGIN RSA PUBLIC KEY-----")
                    || !hostContent.contains("-----END RSA PUBLIC KEY-----")) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                        "Tinc Name、Interface、Mode、Port 或 server_master Subnet 配置无效");
            }
            String ip = subnet.substring(0, subnet.length() - 3);
            if (!IPV4.matcher(ip).matches()) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                        "server_master VPN IPv4 地址无效");
            }
            InetAddress.getByName(ip); // rejects several malformed edge cases after the strict regex
            return new Config(interfaceName, port, ip, true);
        } catch (TincRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "无法读取或解析 Tinc 配置", e);
        }
    }

    private void ensureScriptValid(Path script) throws IOException {
        if (!Files.exists(script, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!isSafeRegularFile(script) || !Files.isExecutable(script)) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_SCRIPT_INVALID,
                    "Tinc 启停脚本权限无效");
        }
        TincCommandResult syntax = run("/usr/bin/sh", "-n", script.toString());
        if (!syntax.isSuccess()) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_SCRIPT_INVALID,
                    "Tinc 启停脚本语法无效");
        }
    }

    private void fixRequiredPermissions(String netName) {
        Path network = networkPath(netName);
        try {
            setModeIfPresent(network.resolve("rsa_key.priv"), PRIVATE_MODE);
            setModeIfPresent(network.resolve("tinc-up"), SCRIPT_MODE);
            setModeIfPresent(network.resolve("tinc-down"), SCRIPT_MODE);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX test/development filesystems are validated by executable checks only.
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_SCRIPT_INVALID,
                    "无法修复 Tinc 私钥或脚本权限", e);
        }
    }

    private void setModeIfPresent(Path file, Set<PosixFilePermission> mode) throws IOException {
        if (Files.exists(file)) {
            Files.setPosixFilePermissions(file, mode);
        }
    }

    private Map<String, String> readAssignments(Path file) throws IOException {
        if (!isSafeRegularFile(file)) {
            throw new IOException("Tinc 配置文件不是安全的普通文件");
        }
        Map<String, String> values = new HashMap<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals > 0) {
                values.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
            }
        }
        return values;
    }

    private void ensureFirewalldRule(int port, String protocol, boolean permanent) {
        List<String> query = new ArrayList<>(Arrays.asList("/usr/bin/firewall-cmd"));
        if (permanent) query.add("--permanent");
        query.add("--query-port=" + port + "/" + protocol);
        TincCommandResult current = executor.execute(query, commandTimeout);
        if (!current.isSuccess()) {
            List<String> add = new ArrayList<>(Arrays.asList("/usr/bin/firewall-cmd"));
            if (permanent) add.add("--permanent");
            add.add("--add-port=" + port + "/" + protocol);
            if (!executor.execute(add, commandTimeout).isSuccess()) {
                throw new TincRuntimeException(TincRuntimeErrorCode.TINC_FIREWALL_FAILED,
                        "无法添加 firewalld " + (permanent ? "永久" : "运行时") + "规则 " + port + "/" + protocol);
            }
        }
        if (!executor.execute(query, commandTimeout).isSuccess()) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_FIREWALL_FAILED,
                    "firewalld 规则验证失败 " + port + "/" + protocol);
        }
    }

    private String detectPacketFilterBackend() {
        try {
            if (run("/usr/sbin/nft", "list", "ruleset").isSuccess()) return "nftables";
        } catch (Exception ignored) { }
        try {
            if (run("/usr/sbin/iptables", "-S").isSuccess()) return "iptables";
        } catch (Exception ignored) { }
        return "未知防火墙后端";
    }

    private boolean isListening(String ssMode, int port) {
        TincCommandResult result = run("/usr/sbin/ss", "-H", ssMode, "sport", "=", ":" + port);
        return result.isSuccess() && !result.getOutput().isEmpty();
    }

    private void checkSocketOwner(int port, String ssMode, long expectedPid) {
        TincCommandResult sockets = run("/usr/sbin/ss", "-H", ssMode, "sport", "=", ":" + port);
        if (sockets.getOutput().isEmpty()) return;
        if (expectedPid <= 0 || !sockets.getOutput().contains("pid=" + expectedPid + ",")) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_PORT_CONFLICT,
                    "端口 " + port + " 已被其他进程占用；不会自动终止未知进程");
        }
    }

    private TincCommandResult run(String... command) {
        return executor.execute(Arrays.asList(command), commandTimeout);
    }

    private boolean success(String... command) {
        return run(command).isSuccess();
    }

    private String unit(String netName) {
        return "tinc@" + netName + ".service";
    }

    private Path networkPath(String netName) {
        requireIdentifier(netName, "网络名称");
        Path path = configRoot.resolve(netName).normalize();
        requireWithinNetwork(netName, path);
        try {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(path)) {
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                            "Tinc 网络目录不能是符号链接");
                }
                Path realPath = path.toRealPath();
                if (!realPath.startsWith(configRoot)) {
                    throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                            "Tinc 网络目录超出配置根目录");
                }
                return realPath;
            }
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "无法解析 Tinc 网络目录", e);
        }
        return path;
    }

    private void requireWithinNetwork(String netName, Path path) {
        Path expected = configRoot.resolve(netName).normalize();
        if (!path.toAbsolutePath().normalize().startsWith(expected)) {
            throw new IllegalArgumentException("Tinc path escapes the configured root");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()
                || WINDOWS_RESERVED_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private static boolean isSafeRegularFile(Path path) {
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requirePort(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Tinc 端口无效");
    }

    private static Integer parsePortOrNull(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parsePositiveLong(String value) {
        try { return Math.max(0, Long.parseLong(value.trim())); }
        catch (Exception e) { return 0; }
    }

    private static String redactName(String name) {
        if (name.length() <= 4) return "***";
        return name.substring(0, 2) + "***" + name.substring(name.length() - 2);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private static TincRuntimeException fromStatus(TincNetworkStatus status) {
        TincRuntimeErrorCode code;
        try { code = TincRuntimeErrorCode.valueOf(status.getFailureCode()); }
        catch (Exception e) { code = TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY; }
        return new TincRuntimeException(code,
                status.getFailureMessage() == null ? "Tinc 运行环境未就绪" : status.getFailureMessage());
    }

    private static final class Config {
        private final String interfaceName;
        private final int port;
        private final String expectedVpnIp;
        private final boolean privateKeyPresent;

        private Config(String interfaceName, int port, String expectedVpnIp, boolean privateKeyPresent) {
            this.interfaceName = interfaceName;
            this.port = port;
            this.expectedVpnIp = expectedVpnIp;
            this.privateKeyPresent = privateKeyPresent;
        }
    }
}

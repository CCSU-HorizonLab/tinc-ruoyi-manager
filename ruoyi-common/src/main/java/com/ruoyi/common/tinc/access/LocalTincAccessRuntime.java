package com.ruoyi.common.tinc.access;

import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.common.tinc.runtime.TincRuntimeErrorCode;
import com.ruoyi.common.tinc.runtime.TincRuntimeException;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import com.ruoyi.common.utils.RsaUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Shared local implementation used by both the management-local mode and Access Agent. */
@Service
public class LocalTincAccessRuntime implements TincAccessRuntime {
    private final TincRuntimeManager runtimeManager;
    private final Path configRoot;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public LocalTincAccessRuntime(TincRuntimeManager runtimeManager,
                                  @Value("${tinc.runtime.config-root:/etc/tinc}") String configRoot) {
        this.runtimeManager = runtimeManager;
        this.configRoot = Paths.get(configRoot).toAbsolutePath().normalize();
    }

    @Override
    public AccessOperationResult createNetwork(AccessNetworkSpec spec) {
        requireSpec(spec);
        ReentrantLock lock = locks.computeIfAbsent(spec.getRuntimeName(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            Path network = safeNetwork(spec.getRuntimeName());
            boolean existed = Files.exists(network, LinkOption.NOFOLLOW_LINKS);
            if (existed) {
                assertSameExistingConfiguration(spec, network);
            } else {
                TincConfigUtils.initNetworkEnv(spec.getPublicAddress(), spec.getRuntimeName());
                Map<String, String> keys = RsaUtils.generateKeys();
                String interfaceName = TincConfigUtils.resolveInterfaceName(spec.getRuntimeName());
                TincConfigUtils.createTincConf(spec.getPublicAddress(), spec.getRuntimeName(),
                        "server_master", "", String.valueOf(spec.getPort()), interfaceName);
                TincConfigUtils.createTincUpAndDown(spec.getPublicAddress(), spec.getRuntimeName(),
                        spec.getSegment() + ".1");
                TincConfigUtils.createHostFile(spec.getPublicAddress(), spec.getRuntimeName(), "server_master",
                        spec.getSegment() + ".1/32", spec.getPublicAddress(),
                        String.valueOf(spec.getPort()), keys.get("publicKey"));
                TincConfigUtils.createPrivateKey(spec.getPublicAddress(), spec.getRuntimeName(), keys.get("privateKey"));
            }
            TincNetworkStatus status = runtimeManager.ensureNetworkReady(spec.getRuntimeName());
            AccessOperationResult result = AccessOperationResult.of(!existed, status);
            result.setServerMasterHost(readServerMaster(spec.getRuntimeName()));
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TincNetworkStatus inspectNetwork(String runtimeName) {
        return runtimeManager.inspectNetworkStatus(AccessRuntimeValidator.name(runtimeName, "网络名称"));
    }

    @Override
    public AccessOperationResult decommissionNetwork(String runtimeName) {
        runtimeName = AccessRuntimeValidator.name(runtimeName, "网络名称");
        ReentrantLock lock = locks.computeIfAbsent(runtimeName, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return AccessOperationResult.of(true, runtimeManager.decommissionNetwork(runtimeName));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public AccessOperationResult upsertPeer(String runtimeName, AccessPeerSpec peer) {
        runtimeName = AccessRuntimeValidator.name(runtimeName, "网络名称");
        if (peer == null) throw new IllegalArgumentException("节点配置不能为空");
        String nodeName = AccessRuntimeValidator.peerName(peer.getNodeName());
        String subnet = AccessRuntimeValidator.subnet32(peer.getSubnet());
        String publicKey = AccessRuntimeValidator.rsaPublicKey(peer.getPublicKey());
        ReentrantLock lock = locks.computeIfAbsent(runtimeName, ignored -> new ReentrantLock());
        lock.lock();
        try {
            boolean changed = TincConfigUtils.applyClientHostFileIfChanged(
                    "local", runtimeName, nodeName, subnet, publicKey);
            return AccessOperationResult.of(changed, runtimeManager.ensureNetworkReady(runtimeName));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public AccessOperationResult deletePeer(String runtimeName, String nodeName) {
        runtimeName = AccessRuntimeValidator.name(runtimeName, "网络名称");
        nodeName = AccessRuntimeValidator.peerName(nodeName);
        ReentrantLock lock = locks.computeIfAbsent(runtimeName, ignored -> new ReentrantLock());
        lock.lock();
        try {
            TincConfigUtils.revokeClientHost("local", runtimeName, nodeName);
            return AccessOperationResult.of(true, runtimeManager.ensureNetworkReady(runtimeName));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public AccessOperationResult reloadNetwork(String runtimeName) {
        runtimeName = AccessRuntimeValidator.name(runtimeName, "网络名称");
        runtimeManager.reloadNetwork(runtimeName);
        return AccessOperationResult.of(true, runtimeManager.ensureNetworkReady(runtimeName));
    }

    @Override
    public String readServerMaster(String runtimeName) {
        runtimeName = AccessRuntimeValidator.name(runtimeName, "网络名称");
        Path host = safeNetwork(runtimeName).resolve("hosts").resolve("server_master").normalize();
        if (!host.startsWith(configRoot) || !Files.isRegularFile(host, LinkOption.NOFOLLOW_LINKS)) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "server_master 配置不存在");
        }
        try {
            return new String(Files.readAllBytes(host), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.TINC_CONFIG_INVALID,
                    "无法读取 server_master 配置", e);
        }
    }

    @Override
    public AccessRuntimeSummary inspectRuntime() {
        AccessRuntimeSummary summary = new AccessRuntimeSummary();
        if (!Files.isDirectory(configRoot, LinkOption.NOFOLLOW_LINKS)) return summary;
        int total = 0;
        int active = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configRoot)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    AccessRuntimeValidator.name(name, "网络名称");
                    if (!Files.isRegularFile(entry.resolve("tinc.conf"), LinkOption.NOFOLLOW_LINKS)) continue;
                    total++;
                    if (runtimeManager.inspectNetworkStatus(name).isSystemdActive()) active++;
                } catch (RuntimeException ignored) {
                    // Ignore unrelated or invalid folders; never expose their paths.
                }
            }
        } catch (IOException e) {
            summary.setAgentStatus("DEGRADED");
        }
        summary.setNetworkCount(total);
        summary.setActiveNetworkCount(active);
        return summary;
    }

    private void requireSpec(AccessNetworkSpec spec) {
        if (spec == null) throw new IllegalArgumentException("网络配置不能为空");
        AccessRuntimeValidator.name(spec.getRuntimeName(), "网络名称");
        AccessRuntimeValidator.address(spec.getPublicAddress());
        AccessRuntimeValidator.segment(spec.getSegment());
        AccessRuntimeValidator.port(spec.getPort());
    }

    private Path safeNetwork(String runtimeName) {
        Path path = configRoot.resolve(runtimeName).normalize();
        if (!path.startsWith(configRoot) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Tinc 网络路径无效");
        }
        return path;
    }

    private void assertSameExistingConfiguration(AccessNetworkSpec spec, Path network) {
        try {
            Path conf = network.resolve("tinc.conf");
            Path host = network.resolve("hosts").resolve("server_master");
            if (!Files.isRegularFile(conf, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(host, LinkOption.NOFOLLOW_LINKS)) {
                throw conflict();
            }
            String confText = new String(Files.readAllBytes(conf), StandardCharsets.UTF_8);
            String hostText = new String(Files.readAllBytes(host), StandardCharsets.UTF_8);
            boolean same = containsAssignment(confText, "Name", "server_master")
                    && containsAssignment(confText, "Mode", "router")
                    && containsAssignment(confText, "Port", String.valueOf(spec.getPort()))
                    && containsAssignment(hostText, "Address", spec.getPublicAddress())
                    && containsAssignment(hostText, "Port", String.valueOf(spec.getPort()))
                    && containsAssignment(hostText, "Subnet", spec.getSegment() + ".1/32");
            if (!same) throw conflict();
        } catch (IOException e) {
            throw new TincRuntimeException(TincRuntimeErrorCode.NETWORK_CONFLICT,
                    "同名网络已存在且无法验证配置", e);
        }
    }

    private boolean containsAssignment(String text, String key, String value) {
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            String[] parts = line.trim().split("\\s*=\\s*", 2);
            if (parts.length == 2 && key.equals(parts[0]) && value.equalsIgnoreCase(parts[1])) return true;
        }
        return false;
    }

    private TincRuntimeException conflict() {
        return new TincRuntimeException(TincRuntimeErrorCode.NETWORK_CONFLICT,
                "同名网络已存在，但运行配置与请求不一致，拒绝覆盖");
    }
}

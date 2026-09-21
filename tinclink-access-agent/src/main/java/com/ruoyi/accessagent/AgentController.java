package com.ruoyi.accessagent;

import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.access.AccessRuntimeSummary;
import com.ruoyi.common.tinc.access.TincAccessRuntime;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

@RestController
@RequestMapping("/api/v1")
public class AgentController {
    private final TincAccessRuntime runtime;
    private final AgentProperties properties;

    public AgentController(TincAccessRuntime runtime, AgentProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
    }

    @GetMapping("/health")
    public AccessAgentHealth health() {
        AccessRuntimeSummary summary = runtime.inspectRuntime();
        Runtime jvm = Runtime.getRuntime();
        long max = jvm.maxMemory();
        long used = jvm.totalMemory() - jvm.freeMemory();
        AccessAgentHealth health = new AccessAgentHealth();
        health.setStatus(summary.getAgentStatus());
        health.setAgentId(properties.getId());
        health.setAgentVersion(properties.getVersion());
        health.setHostname(hostname());
        health.setOs(System.getProperty("os.name") + " " + System.getProperty("os.version"));
        health.setUptime(ManagementFactory.getRuntimeMXBean().getUptime());
        health.setCpuUsage(systemLoad());
        health.setMemoryUsage(max <= 0 ? 0 : round((used * 100.0) / max));
        health.setNetworkCount(summary.getNetworkCount());
        health.setTimestamp(System.currentTimeMillis());
        return health;
    }

    @GetMapping("/runtime")
    public AccessRuntimeSummary runtime() { return runtime.inspectRuntime(); }

    @PostMapping("/networks")
    public AccessOperationResult createNetwork(@RequestBody AccessNetworkSpec spec) {
        return runtime.createNetwork(spec);
    }

    @GetMapping("/networks/{runtimeName}/status")
    public TincNetworkStatus status(@PathVariable String runtimeName) {
        return runtime.inspectNetwork(runtimeName);
    }

    @GetMapping("/networks/{runtimeName}/server-master")
    public AccessOperationResult serverMaster(@PathVariable String runtimeName) {
        AccessOperationResult result = AccessOperationResult.of(false, runtime.inspectNetwork(runtimeName));
        result.setServerMasterHost(runtime.readServerMaster(runtimeName));
        return result;
    }

    @DeleteMapping("/networks/{runtimeName}")
    public AccessOperationResult decommission(@PathVariable String runtimeName) {
        return runtime.decommissionNetwork(runtimeName);
    }

    @PostMapping("/networks/{runtimeName}/peers")
    public AccessOperationResult upsertPeer(@PathVariable String runtimeName,
                                            @RequestBody AccessPeerSpec peer) {
        return runtime.upsertPeer(runtimeName, peer);
    }

    @DeleteMapping("/networks/{runtimeName}/peers/{nodeName}")
    public AccessOperationResult deletePeer(@PathVariable String runtimeName,
                                            @PathVariable String nodeName) {
        return runtime.deletePeer(runtimeName, nodeName);
    }

    @PostMapping("/networks/{runtimeName}/reload")
    public AccessOperationResult reload(@PathVariable String runtimeName) {
        return runtime.reloadNetwork(runtimeName);
    }

    private String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "unknown"; }
    }

    private double systemLoad() {
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        int processors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
        if (load < 0 || processors <= 0) return -1;
        return round(Math.min(100.0, load * 100.0 / processors));
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}

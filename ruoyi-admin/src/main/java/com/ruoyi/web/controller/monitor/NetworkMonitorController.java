package com.ruoyi.web.controller.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.platform.ManagedResourceId;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc.security.TincResourceAccess;

/** Read-only runtime observations; missing telemetry must never become synthetic measurements. */
@RestController
@RequestMapping("/monitor/network")
@PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:list')")
public class NetworkMonitorController extends BaseController {
    @Autowired private ITincNetworkMangeService tincNetworkMangeService;
    @Autowired private ITincNodeMangeService tincNodeMangeService;
    @Autowired private IMangeServerService mangeServerService;
    @Autowired private TincRuntimeRouter runtimeRouter;
    @Autowired private TincResourceAccess resourceAccess;

    @GetMapping("/global-stats")
    public AjaxResult getGlobalStats() {
        TincNetworkMange networkFilter = new TincNetworkMange();
        resourceAccess.scope(networkFilter);
        MangeServer serverFilter = new MangeServer();
        resourceAccess.scopeServerOptions(serverFilter);
        List<TincNetworkMange> networks = tincNetworkMangeService.selectTincNetworkMangeList(networkFilter);
        List<MangeServer> servers = mangeServerService.selectMangeServerList(serverFilter);
        Map<Long, MangeServer> serversById = new HashMap<>();
        int reportedOnlineAgents = 0;
        for (MangeServer server : servers) {
            serversById.put(server.getId(), server);
            // Stored Agent health, NOT proof of current data-plane connectivity.
            if ("ONLINE".equals(server.getAgentStatus())) reportedOnlineAgents++;
        }
        List<Map<String, Object>> observations = new ArrayList<>();
        int ready = 0;
        int unknown = 0;
        for (TincNetworkMange network : networks) {
            Map<String, Object> observation = inspect(network, serversById.get(network.getServerId()));
            observations.add(observation);
            if ("READY".equals(observation.get("readiness"))) ready++;
            if ("UNKNOWN".equals(observation.get("readiness"))) unknown++;
        }
        Map<String, Object> stats = unavailableTelemetry();
        stats.put("totalNetworks", networks.size());
        stats.put("onlineNetworks", ready); // Compatibility alias: confirmed READY, not client reachability.
        stats.put("onlineRate", networks.isEmpty() ? null : ready * 100 / networks.size());
        stats.put("unknownNetworks", unknown);
        stats.put("notReadyNetworks", networks.size() - ready - unknown);
        stats.put("networks", observations);
        stats.put("totalServers", servers.size());
        stats.put("reportedOnlineAgents", reportedOnlineAgents);
        stats.put("agentStatusSource", "DATABASE_LAST_PROBE");
        TincNodeMange nodeFilter = new TincNodeMange();
        resourceAccess.scope(nodeFilter);
        stats.put("nodeSummary", nodeSummary(tincNodeMangeService.selectTincNodeMangeList(nodeFilter)));
        stats.put("observedAt", System.currentTimeMillis());
        return AjaxResult.success(stats);
    }

    @GetMapping("/single-stats/{networkId}")
    public AjaxResult getSingleStats(@PathVariable("networkId") Long networkId) {
        resourceAccess.requireNetwork(networkId);
        if (networkId == null || networkId <= 0) return AjaxResult.error("无效的网络 ID");
        TincNetworkMange network = tincNetworkMangeService.selectTincNetworkMangeById(networkId);
        if (network == null) return AjaxResult.error("未找到对应的内网信息");
        MangeServer server = network.getServerId() == null ? null
                : mangeServerService.selectMangeServerById(network.getServerId());
        Map<String, Object> stats = unavailableTelemetry();
        stats.putAll(inspect(network, server));
        stats.put("isOnline", "UNKNOWN".equals(stats.get("readiness")) ? null
                : "READY".equals(stats.get("readiness")));
        TincNodeMange filter = new TincNodeMange();
        filter.setNetworkId(networkId);
        resourceAccess.scope(filter);
        stats.put("nodeSummary", nodeSummary(tincNodeMangeService.selectTincNodeMangeList(filter)));
        // Legacy shapes are retained, with null values to distinguish uncollected from zero.
        stats.put("responseTime", missingValues("value", "progress", "avg", "max", "min"));
        stats.put("healthScore", missingValues("value", "progress", "network", "test", "security"));
        stats.put("traffic", missingValues("value", "progress", "upload", "total", "bandwidth"));
        stats.put("nodeRate", missingValues("value", "progress", "online", "offline", "total"));
        return AjaxResult.success(stats);
    }

    private Map<String, Object> inspect(TincNetworkMange network, MangeServer server) {
        Map<String, Object> result = new HashMap<>();
        result.put("networkId", network.getId());
        // Add a platform identity without changing the legacy Tinc networkId contract.
        result.put("resource", ManagedResourceId.of("tinc", "overlay-network", network.getId()));
        result.put("serverId", network.getServerId());
        result.put("networkName", network.getNetworkName());
        result.put("readiness", "UNKNOWN");
        result.put("failureCode", "STATUS_QUERY_FAILED");
        result.put("failureMessage", "运行状态查询失败，请检查接入服务器和 Agent");
        result.put("source", "RUNTIME_PROBE");
        try {
            if (server == null) {
                result.put("failureCode", "LEGACY_RELATION_MISSING");
                result.put("failureMessage", "网络缺少有效的接入服务器关联");
            } else {
                TincNetworkStatus status = runtimeRouter.inspectNetwork(server, network.getNetworkName());
                if (status != null && ("READY".equals(status.getReadiness())
                        || "NOT_READY".equals(status.getReadiness())
                        || "DEGRADED".equals(status.getReadiness()))) {
                    result.put("readiness", status.getReadiness());
                    result.put("failureCode", safeCode(status.getFailureCode()));
                    result.put("failureMessage", status.isReady() ? null : "运行时未就绪，请查看网络诊断");
                }
            }
        } catch (RuntimeException failure) {
            // Never relay remote bodies, tokens, paths or exception messages.
            logger.warn("网络监控查询失败: networkId={}, errorType={}",
                    network.getId(), failure.getClass().getSimpleName());
        }
        result.put("observedAt", System.currentTimeMillis());
        return result;
    }

    private String safeCode(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,79}") ? code : null;
    }

    private Map<String, Object> nodeSummary(List<TincNodeMange> nodes) {
        int configured = 0;
        for (TincNodeMange node : nodes) {
            if ("已配置".equals(node.getStatus()) || "1".equals(node.getStatus())) configured++;
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", nodes.size());
        summary.put("configured", configured);
        summary.put("source", "DATABASE");
        // There is no expiring heartbeat observation in the current node model.
        summary.put("online", null);
        return summary;
    }

    private Map<String, Object> unavailableTelemetry() {
        Map<String, Object> stats = missingValues("averageResponseTime", "healthScore", "averageRecoveryTime");
        stats.put("telemetryCollected", false);
        stats.put("healthStatus", "未采集");
        stats.put("responseTimeChartData", emptyChart());
        stats.put("interruptChartData", emptyChart());
        stats.put("interruptEventData", emptyChart());
        return stats;
    }

    private Map<String, Object> missingValues(String... fields) {
        Map<String, Object> result = new HashMap<>();
        for (String field : fields) result.put(field, null);
        return result;
    }

    private Map<String, Object> emptyChart() {
        Map<String, Object> result = new HashMap<>();
        result.put("expectedData", Collections.emptyList());
        result.put("actualData", Collections.emptyList());
        return result;
    }
}

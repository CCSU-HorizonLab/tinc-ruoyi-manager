package com.ruoyi.web.controller.system;

import com.ruoyi.tinc.validation.TincModelValidator;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A single member query defines both the returned routes and their content revision. */
@Component
public class TincClientRouteSnapshot {
    private static final int MAX_ROUTES = 1024;
    @Autowired private ITincNodeMangeService nodeService;

    public Snapshot build(TincNodeMange current, TincNetworkMange network) {
        if (current == null || current.getId() == null || network == null || network.getId() == null
                || (current.getNetworkId() != null && !network.getId().equals(current.getNetworkId()))
                || !eligible(current)) {
            throw new IllegalStateException("客户端节点没有有效的网络授权");
        }
        String segment = TincModelValidator.requireSegment(network.getSegment());
        TincModelValidator.requireNodeIp(current.getNetworkIp(), segment);
        if (!clientRouteAllowed(current.getNetworkIp()) || !clientRouteAllowed(segment + ".1")) {
            throw new IllegalStateException("网络地址不是客户端可接受的路由目标");
        }
        String gateway = segment + ".1/32";
        TincNodeMange query = new TincNodeMange();
        query.setNetworkId(network.getId());
        List<TincNodeMange> members = nodeService.selectTincNodeMangeList(query);
        if (members == null) throw new IllegalStateException("网络成员查询失败");

        TreeSet<String> peers = new TreeSet<>();
        for (TincNodeMange member : members) {
            if (member == null || !Objects.equals(network.getId(), member.getNetworkId())) continue;
            if (Objects.equals(current.getId(), member.getId())) {
                continue;
            }
            if (!eligible(member)) continue;
            try {
                TincModelValidator.requireNodeIp(member.getNetworkIp(), segment);
            } catch (IllegalArgumentException badAddress) {
                continue;
            }
            if (clientRouteAllowed(member.getNetworkIp())
                    && !Objects.equals(current.getNetworkIp(), member.getNetworkIp())) {
                peers.add(member.getNetworkIp() + "/32");
                if (peers.size() > MAX_ROUTES - 1) throw new IllegalStateException("授权路由数量超过上限");
            }
        }
        List<String> routes = new ArrayList<>(peers.size() + 1);
        routes.add(gateway);
        routes.addAll(peers);
        StringBuilder canonical = new StringBuilder();
        canonical.append("gateway=").append(gateway).append('\n');
        for (String peer : peers) canonical.append("peer=").append(peer).append('\n');
        return new Snapshot(Collections.unmodifiableList(routes),
                "routes-" + sha256(canonical.toString()));
    }

    public static boolean eligible(TincNodeMange node) {
        if (node == null || node.getId() == null) return false;
        return !denied(node.getStatus()) && !denied(node.getNodeStatus());
    }

    private static boolean denied(String state) {
        return "已撤销".equals(state) || "禁用".equals(state)
                || "revoked".equalsIgnoreCase(state) || "disabled".equalsIgnoreCase(state);
    }

    /** Keep the server snapshot within the existing Rust client's /32 destination policy. */
    private static boolean clientRouteAllowed(String ip) {
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return !"0.0.0.0".equals(ip) && !"255.255.255.255".equals(ip)
                && first != 127 && (first < 224 || first > 239)
                && !(first == 169 && second == 254);
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("路由版本计算失败", e);
        }
    }

    public static final class Snapshot {
        private final List<String> routes;
        private final String revision;

        private Snapshot(List<String> routes, String revision) {
            this.routes = routes;
            this.revision = revision;
        }

        public List<String> getRoutes() { return routes; }
        public String getRevision() { return revision; }
    }
}

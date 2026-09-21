package com.ruoyi.tinc_node.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc.validation.TincModelValidator;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;

/**
 * Tinc节点集群管理Service业务层处理
 */
@Service
public class TincNodeMangeServiceImpl implements ITincNodeMangeService
{
    private static final Logger log = LoggerFactory.getLogger(TincNodeMangeServiceImpl.class);

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private IMangeServerService mangeServerService;

    @Autowired
    private TincRuntimeRouter tincRuntimeRouter;

    @Autowired(required = false)
    private RedisCache redisCache;

    private static final String CLIENT_TOKEN_PREFIX = "tinc:client:token:";
    private static final String CLIENT_NODE_TOKEN_PREFIX = "tinc:client:node-tokens:";
    private static final String CLIENT_REVOKED_TOKEN_PREFIX = "tinc:client:revoked-token:";
    private static final int REVOKED_TOKEN_TOMBSTONE_HOURS = 24;

    @Override
    public TincNodeMange selectTincNodeMangeById(Long id) {
        return tincNodeMangeMapper.selectTincNodeMangeById(id);
    }

    @Override
    public List<TincNodeMange> selectTincNodeMangeList(TincNodeMange tincNodeMange) {
        return tincNodeMangeMapper.selectTincNodeMangeList(tincNodeMange);
    }

    @Override
    public List<TincNodeMange> selectByNodeNameExact(String nodeName) {
        return tincNodeMangeMapper.selectByNodeNameExact(nodeName);
    }

    /**
     * 新增 Tinc 节点（仅入库，密钥由 Qt 客户端自行生成并通过 API 上传）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTincNodeMange(TincNodeMange tincNodeMange) {
        TincNetworkMange network = requireNetworkForUpdate(tincNodeMange.getNetworkId());
        tincNodeMange.setNetworkName(network.getNetworkName());
        MangeServer server = requireServer(network);
        tincNodeMange.setServerName(server.getServerName());
        tincNodeMange.setNodeName(TincModelValidator.requireNodeName(tincNodeMange.getNodeName()));
        tincNodeMange.setNetworkIp(TincModelValidator.requireNodeIp(
                tincNodeMange.getNetworkIp(), network.getSegment()));
        validateNodeUniqueness(tincNodeMange);
        tincNodeMange.setCreateTime(DateUtils.getNowDate());
        int rows = tincNodeMangeMapper.insertTincNodeMange(tincNodeMange);
        log.info("节点 [{}] 基础信息创建成功，等待客户端上传公钥...", tincNodeMange.getNodeName());
        return rows;
    }

    /**
     * 修改 Tinc 节点（兼容两种场景）：
     *
     * <p>场景 A — 客户端手动上传公钥：</p>
     * <ol>
     *   <li>前端把脏公钥文本塞进 password 字段</li>
     *   <li>正则抠出纯净 PEM 块</li>
     *   <li>覆写本地 hosts 文件副本</li>
     *   <li>★ 推送到远程 Tinc VPN 网关</li>
     *   <li>★ 远程重载 tincd 使新节点公钥生效</li>
     * </ol>
     *
     * <p>场景 B — 普通字段更新（改状态/改备注等）：</p>
     * <ul>
     *   <li>password 字段不包含 PEM 公钥标记，直接透传给 Mapper</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTincNodeMange(TincNodeMange tincNodeMange) {
        TincNodeMange oldNode = tincNodeMangeMapper.selectTincNodeMangeById(tincNodeMange.getId());
        if (oldNode == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        prohibitRuntimeIdentityChange(tincNodeMange, oldNode);
        TincNetworkMange network = resolveNetwork(oldNode);
        MangeServer server = requireServer(network);
        tincNodeMange.setNetworkId(network.getId());
        tincNodeMange.setNetworkName(network.getNetworkName());
        tincNodeMange.setServerName(server.getServerName());

        if (tincNodeMange.getNetworkIp() != null && !tincNodeMange.getNetworkIp().isEmpty()) {
            tincNodeMange.setNetworkIp(TincModelValidator.requireNodeIp(
                    tincNodeMange.getNetworkIp(), network.getSegment()));
            validateNodeUniqueness(tincNodeMange);
        }
        String dirtyText = tincNodeMange.getPassword();

        // 判断是否为公钥上传场景：password 字段里是否包含 PEM 公钥头尾
        if (dirtyText != null && dirtyText.contains("-----BEGIN") && dirtyText.contains("PUBLIC KEY-----")) {

            // 1. 查出旧节点信息（获取 netName, nodeName, 旧的 IP）
            String netName  = network.getNetworkName();
            String nodeName = oldNode.getNodeName();
            String rawIp    = oldNode.getNetworkIp();
            String nodeIp   = (rawIp != null && rawIp.contains("/")) ? rawIp : rawIp + "/32";

            // 2. 正则精准抠出纯净公钥块
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?s)(-----BEGIN(?: RSA)? PUBLIC KEY-----.*?-----END(?: RSA)? PUBLIC KEY-----)");
            java.util.regex.Matcher matcher = pattern.matcher(dirtyText);

            if (!matcher.find()) {
                throw new RuntimeException("未识别到有效的公钥格式，请检查上传内容");
            }
            String cleanPubKey = matcher.group(0);

            AccessPeerSpec peer = new AccessPeerSpec();
            peer.setNodeName(nodeName);
            peer.setSubnet(nodeIp);
            peer.setPublicKey(cleanPubKey);
            tincRuntimeRouter.upsertPeer(server, netName, peer);

            // 5. 仅更新业务状态，绝不将公钥写入数据库
            tincNodeMange.setPassword(null);
            tincNodeMange.setStatus("已配置");
            int rows = tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
        log.info("节点 [{}] 公钥已推送至对应网关的 hosts 目录", nodeName);
            return rows;
        }

        // 场景 B：普通更新，直接透传
        return tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNodeMangeByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            TincNodeMange node = tincNodeMangeMapper.selectTincNodeMangeById(id);
            if (node != null) {
                revokeRuntimeNode(node);
            }
        }
        return tincNodeMangeMapper.deleteTincNodeMangeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNodeMangeById(Long id) {
        TincNodeMange node = tincNodeMangeMapper.selectTincNodeMangeById(id);
        if (node != null) {
            revokeRuntimeNode(node);
        }
        return tincNodeMangeMapper.deleteTincNodeMangeById(id);
    }

    /**
     * 根据网络名称查询对应的网关服务器 IP
     * <p>
     * 链路：tinc_node → tinc_network → server_name → mange_server → server_ip
     * </p>
     */
    private TincNetworkMange requireNetwork(Long networkId) {
        if (networkId == null) {
            throw new IllegalArgumentException("请选择有效的网络 ID");
        }
        TincNetworkMange network = tincNetworkMangeMapper.selectTincNetworkMangeById(networkId);
        if (network == null) {
            throw new IllegalArgumentException("所属网络不存在: id=" + networkId);
        }
        return network;
    }

    private TincNetworkMange requireNetworkForUpdate(Long networkId) {
        if (networkId == null) {
            throw new IllegalArgumentException("请选择有效的网络 ID");
        }
        TincNetworkMange network = tincNetworkMangeMapper.selectTincNetworkMangeByIdForUpdate(networkId);
        if (network == null) {
            throw new IllegalArgumentException("所属网络不存在: id=" + networkId);
        }
        return network;
    }

    /** 历史 network_id 为空时仅允许名称唯一精确回退；绝不使用 LIKE 或任取第一条。 */
    private TincNetworkMange resolveNetwork(TincNodeMange node) {
        if (node.getNetworkId() != null) {
            return requireNetwork(node.getNetworkId());
        }
        log.warn("节点缺少 network_id，使用已弃用的精确名称回退: nodeId={}", node.getId());
        List<TincNetworkMange> matches = tincNetworkMangeMapper.selectByNetworkNameExact(node.getNetworkName());
        if (matches == null || matches.size() != 1) {
            throw new IllegalStateException("历史节点无法唯一解析所属网络");
        }
        return matches.get(0);
    }

    private MangeServer requireServer(TincNetworkMange network) {
        if (network.getServerId() != null) {
            MangeServer server = mangeServerService.selectMangeServerById(network.getServerId());
            if (server == null) {
                throw new IllegalStateException("网络关联的服务器不存在");
            }
            return server;
        }
        log.warn("网络缺少 server_id，使用已弃用的精确名称回退: networkId={}", network.getId());
        MangeServer server = mangeServerService.selectByServerNameExact(network.getServerName());
        if (server == null) {
            throw new IllegalStateException("历史网络无法唯一解析接入服务器");
        }
        return server;
    }

    private void validateNodeUniqueness(TincNodeMange node) {
        if (tincNodeMangeMapper.countNodeNameInNetwork(
                node.getNetworkId(), node.getNodeName(), node.getId()) > 0) {
            throw new IllegalStateException("TINC_NODE_NAME_CONFLICT: 同一网络内节点名称必须唯一");
        }
        if (tincNodeMangeMapper.countNetworkIpInNetwork(
                node.getNetworkId(), node.getNetworkIp(), node.getId()) > 0) {
            throw new IllegalStateException("TINC_NODE_IP_CONFLICT: 同一网络内节点地址必须唯一");
        }
    }

    private void prohibitRuntimeIdentityChange(TincNodeMange update, TincNodeMange oldNode) {
        if (update.getNodeName() != null && !oldNode.getNodeName().equals(update.getNodeName())) {
            throw new IllegalStateException("TINC_NODE_RENAME_FORBIDDEN: 节点运行名称不能通过普通修改变更");
        }
        if (update.getNetworkId() != null && oldNode.getNetworkId() != null
                && !oldNode.getNetworkId().equals(update.getNetworkId())) {
            throw new IllegalStateException("TINC_NODE_MOVE_FORBIDDEN: 节点不能通过普通修改迁移网络");
        }
        if (update.getNetworkName() != null && !oldNode.getNetworkName().equals(update.getNetworkName())) {
            throw new IllegalStateException("TINC_NODE_MOVE_FORBIDDEN: 节点不能通过普通修改迁移网络");
        }
    }

    private void revokeRuntimeNode(TincNodeMange node) {
        TincNetworkMange network = resolveNetwork(node);
        MangeServer server = requireServer(network);
        tincRuntimeRouter.deletePeer(server, network.getNetworkName(), node.getNodeName());
        revokeClientTokens(node.getId());
        log.info("Tinc 节点运行授权已撤销: id={}, nodeName={}, netName={}",
                node.getId(), node.getNodeName(), network.getNetworkName());
    }

    private void revokeClientTokens(Long nodeId) {
        if (redisCache == null || nodeId == null) return;
        String indexKey = CLIENT_NODE_TOKEN_PREFIX + nodeId;
        java.util.Set<String> hashes = redisCache.getCacheSet(indexKey);
        if (hashes != null) {
            for (String hash : hashes) {
                redisCache.setCacheObject(CLIENT_REVOKED_TOKEN_PREFIX + hash, String.valueOf(nodeId),
                        REVOKED_TOKEN_TOMBSTONE_HOURS, TimeUnit.HOURS);
                redisCache.deleteObject(CLIENT_TOKEN_PREFIX + hash);
            }
        }
        redisCache.deleteObject(indexKey);
    }
}

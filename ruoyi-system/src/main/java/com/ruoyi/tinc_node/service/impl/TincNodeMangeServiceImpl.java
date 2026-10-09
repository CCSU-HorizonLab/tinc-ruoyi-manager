package com.ruoyi.tinc_node.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import java.security.SecureRandom;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.security.TincNodePasswordCodec;
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

    private static final String ACCESS_CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_GENERATE_RETRIES = 10;

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

    @Override
    public TincNodeMange selectByAccessCode(String accessCode) {
        if (StringUtils.isEmpty(accessCode)) {
            return null;
        }
        return tincNodeMangeMapper.selectByAccessCode(accessCode.trim().toUpperCase());
    }

    @Override
    public boolean verifyClientPassword(TincNodeMange node, String rawPassword) {
        if (node == null || node.getId() == null
                || !TincNodePasswordCodec.matches(rawPassword, node.getPassword())) {
            return false;
        }
        if (!TincNodePasswordCodec.isEncoded(node.getPassword())) {
            // A concurrent reset must not be overwritten by the compatibility upgrade.
            return tincNodeMangeMapper.upgradeLegacyPassword(node.getId(),
                    TincNodePasswordCodec.legacyFingerprint(node.getPassword()),
                    TincNodePasswordCodec.encode(rawPassword)) == 1;
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotateAccessCode(Long nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("节点 ID 不能为空");
        }
        TincNodeMange node = tincNodeMangeMapper.selectTincNodeMangeById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: id=" + nodeId);
        }
        String newCode = generateUniqueAccessCode();
        if (tincNodeMangeMapper.updateAccessCode(nodeId, newCode, DateUtils.getNowDate()) != 1) {
            throw new IllegalStateException("接入码轮换失败，节点状态已变化");
        }
        revokeClientTokens(nodeId);
        log.info("节点接入码已轮换: nodeId={}, nodeName={}", nodeId, node.getNodeName());
        return newCode;
    }

    public String generateUniqueAccessCode() {
        for (int retry = 0; retry < MAX_GENERATE_RETRIES; retry++) {
            StringBuilder sb = new StringBuilder("TL-");
            for (int i = 0; i < 4; i++) {
                sb.append(ACCESS_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(ACCESS_CODE_CHARS.length())));
            }
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ACCESS_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(ACCESS_CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (tincNodeMangeMapper.countByAccessCode(candidate, null) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成唯一的节点接入码，请稍后重试");
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

        tincNodeMange.setPassword(TincNodePasswordCodec.encode(tincNodeMange.getPassword()));

        if (StringUtils.isEmpty(tincNodeMange.getAccessCode())) {
            tincNodeMange.setAccessCode(generateUniqueAccessCode());
        } else {
            String normalized = tincNodeMange.getAccessCode().trim().toUpperCase();
            if (tincNodeMangeMapper.countByAccessCode(normalized, null) > 0) {
                throw new IllegalStateException("TINC_NODE_ACCESS_CODE_CONFLICT: 接入码已存在，必须全局唯一");
            }
            tincNodeMange.setAccessCode(normalized);
        }

        tincNodeMange.setCreateTime(DateUtils.getNowDate());
        int rows = tincNodeMangeMapper.insertTincNodeMange(tincNodeMange);
        log.info("节点 [{}] 基础信息创建成功，已分配接入码，等待客户端上传公钥...", tincNodeMange.getNodeName());
        return rows;
    }

    /** Update metadata and rotate the login credential only when a new password is supplied. */
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
        tincNodeMange.setAccessCode(oldNode.getAccessCode());

        if (tincNodeMange.getNetworkIp() != null && !tincNodeMange.getNetworkIp().isEmpty()) {
            tincNodeMange.setNetworkIp(TincModelValidator.requireNodeIp(
                    tincNodeMange.getNetworkIp(), network.getSegment()));
            validateNodeUniqueness(tincNodeMange);
        }
        boolean passwordChanged = !StringUtils.isEmpty(tincNodeMange.getPassword());
        if (!passwordChanged) {
            tincNodeMange.setPassword(null);
        } else {
            tincNodeMange.setPassword(TincNodePasswordCodec.encode(tincNodeMange.getPassword()));
        }
        int updated = tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
        if (updated > 0 && passwordChanged) {
            revokeClientTokens(tincNodeMange.getId());
        }
        return updated;
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
        if (update.getAccessCode() != null && oldNode.getAccessCode() != null
                && !oldNode.getAccessCode().equalsIgnoreCase(update.getAccessCode().trim())) {
            throw new IllegalStateException("TINC_NODE_ACCESS_CODE_CHANGE_FORBIDDEN: 节点接入码不能通过普通修改变更");
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

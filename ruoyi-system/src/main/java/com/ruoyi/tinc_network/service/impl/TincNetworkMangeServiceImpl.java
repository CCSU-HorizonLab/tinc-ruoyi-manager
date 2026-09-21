package com.ruoyi.tinc_network.service.impl;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc.validation.TincModelValidator;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;

import com.ruoyi.tinc_server.service.*;
import com.ruoyi.tinc_server.domain.*;

/**
 * Tinc内网集群管理Service业务层处理
 *
 * @author sun
 */
@Service
public class TincNetworkMangeServiceImpl implements ITincNetworkMangeService
{
    private static final Logger log = LoggerFactory.getLogger(TincNetworkMangeServiceImpl.class);

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private IMangeServerService mangeServerService;

    @Autowired
    private TincRuntimeRouter tincRuntimeRouter;

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    @Override
    public TincNetworkMange selectTincNetworkMangeById(Long id)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeById(id);
    }

    @Override
    public List<TincNetworkMange> selectTincNetworkMangeList(TincNetworkMange tincNetworkMange)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeList(tincNetworkMange);
    }

    @Override
    public TincNetworkMange selectByNetworkNameExact(String networkName)
    {
        List<TincNetworkMange> matches = tincNetworkMangeMapper.selectByNetworkNameExact(networkName);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("网络名称存在重复记录，无法建立稳定关联");
        }
        return matches.get(0);
    }

    /**
     * 新增 Tinc 网络：先建立稳定 Server 关系，再由 Runtime Router 在所选
     * LOCAL Runtime 或 Access Agent 上创建并验证运行网络。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTincNetworkMange(TincNetworkMange network)
    {
        tincNetworkMangeMapper.lockAllServerIdsForNetworkMutation();
        MangeServer selectedServer = requireServer(network.getServerId());
        network.setServerName(selectedServer.getServerName());
        network.setNetworkName(TincModelValidator.requireNewRuntimeName(network.getNetworkName(), "网络名称"));
        network.setPort(TincModelValidator.requirePort(network.getPort()));
        network.setSegment(TincModelValidator.requireSegment(network.getSegment()));
        validateNetworkResources(network, selectedServer);

        // 1. 所有稳定关系和冲突检查通过后再入库，避免先污染数据库或覆盖 Runtime。
        network.setCreateTime(DateUtils.getNowDate());
        network.setNetworkStatus("初始化中");
        int rows = tincNetworkMangeMapper.insertTincNetworkMange(network);

        // 2. 只在所选 Server 对应的 Runtime 创建配置并等待 READY。
        try {
            initializeNetworkRuntime(network, selectedServer);
            network.setNetworkStatus("READY");
            tincNetworkMangeMapper.updateTincNetworkMange(network);

        } catch (Exception e) {
            // 手动回滚：文件生成或推送失败时，回滚 DB 记录
            throw new RuntimeException("内网初始化失败: " + e.getMessage());
        }

        return rows;
    }

    /** 独立封装副作用，便于对稳定关系与校验逻辑做无文件系统单元测试。 */
    protected void initializeNetworkRuntime(TincNetworkMange network, MangeServer selectedServer)
    {
        AccessNetworkSpec spec = new AccessNetworkSpec();
        spec.setRuntimeName(network.getNetworkName());
        spec.setPublicAddress(selectedServer.getServerIp());
        spec.setPort(Integer.parseInt(network.getPort()));
        spec.setSegment(network.getSegment());
        AccessOperationResult result = tincRuntimeRouter.createNetwork(selectedServer, spec);
        if (result == null || result.getNetworkStatus() == null || !result.getNetworkStatus().isReady()) {
            throw new IllegalStateException("NETWORK_NOT_READY: Access Server 未达到 READY");
        }
    }

    /** 修改非 Runtime 字段；运行名称、Server、端口和网段必须走独立迁移流程。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTincNetworkMange(TincNetworkMange tincNetworkMange)
    {
        // 1. 查出修改前的老数据
        tincNetworkMangeMapper.lockAllServerIdsForNetworkMutation();
        TincNetworkMange oldNetwork = tincNetworkMangeMapper.selectTincNetworkMangeByIdForUpdate(tincNetworkMange.getId());
        if (oldNetwork == null) {
            throw new RuntimeException("修改的网络不存在！");
        }

        if (tincNetworkMange.getNetworkName() != null
                && !oldNetwork.getNetworkName().equals(tincNetworkMange.getNetworkName())) {
            throw new IllegalStateException("TINC_NETWORK_RENAME_FORBIDDEN: 网络运行名称不能通过普通修改变更");
        }
        tincNetworkMange.setNetworkName(oldNetwork.getNetworkName());
        if (tincNetworkMange.getServerId() == null) {
            tincNetworkMange.setServerId(oldNetwork.getServerId());
        }
        MangeServer selectedServer = requireServer(tincNetworkMange.getServerId());
        tincNetworkMange.setServerName(selectedServer.getServerName());
        if (tincNetworkMange.getPort() == null || tincNetworkMange.getPort().isEmpty()) {
            tincNetworkMange.setPort(oldNetwork.getPort());
        }
        if (tincNetworkMange.getSegment() == null || tincNetworkMange.getSegment().isEmpty()) {
            tincNetworkMange.setSegment(oldNetwork.getSegment());
        }
        tincNetworkMange.setPort(TincModelValidator.requirePort(tincNetworkMange.getPort()));
        tincNetworkMange.setSegment(TincModelValidator.requireSegment(tincNetworkMange.getSegment()));
        if (oldNetwork.getServerId() != null && !oldNetwork.getServerId().equals(tincNetworkMange.getServerId())) {
            throw new IllegalStateException("TINC_NETWORK_MOVE_FORBIDDEN: 网络不能通过普通修改迁移 Access Server");
        }
        if (!oldNetwork.getPort().equals(tincNetworkMange.getPort())
                || !oldNetwork.getSegment().equals(tincNetworkMange.getSegment())) {
            throw new IllegalStateException("TINC_NETWORK_RUNTIME_CHANGE_FORBIDDEN: 端口和网段需要独立迁移流程");
        }
        validateNetworkResources(tincNetworkMange, selectedServer);

        // 2. 执行数据库更新
        int rows = tincNetworkMangeMapper.updateTincNetworkMange(tincNetworkMange);

        TincNetworkStatus status = tincRuntimeRouter.inspectNetwork(selectedServer, oldNetwork.getNetworkName());
        if (!status.isReady()) throw new IllegalStateException("NETWORK_NOT_READY: " + status.getFailureMessage());
        tincNetworkMange.setNetworkStatus("READY");
        tincNetworkMangeMapper.updateTincNetworkMange(tincNetworkMange);

        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNetworkMangeByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            validateNetworkRecordDeletion(id);
        }
        return tincNetworkMangeMapper.deleteTincNetworkMangeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNetworkMangeById(Long id) {
        validateNetworkRecordDeletion(id);
        return tincNetworkMangeMapper.deleteTincNetworkMangeById(id);
    }

    /**
     * 删除前要求无节点，并由对应 Runtime 安全停用服务；只删除管理记录，
     * 绝不删除 /etc/tinc 下的配置、hosts 或私钥。
     */
    private void validateNetworkRecordDeletion(Long id) {
        TincNetworkMange network = tincNetworkMangeMapper.selectTincNetworkMangeByIdForUpdate(id);
        if (network == null) {
            return;
        }

        if (tincNodeMangeMapper.countByNetworkId(id) > 0) {
            throw new IllegalStateException("TINC_NETWORK_IN_USE: 网络 [" + network.getNetworkName()
                    + "] 仍有关联节点，请先删除或迁移节点");
        }

        MangeServer server = requireServer(network.getServerId());
        AccessOperationResult decommissioned = tincRuntimeRouter.decommissionNetwork(server, network.getNetworkName());
        if (decommissioned == null || decommissioned.getNetworkStatus() == null
                || !"DECOMMISSIONED".equals(decommissioned.getNetworkStatus().getReadiness())) {
            throw new IllegalStateException("TINC_NETWORK_ACTIVE: Access Server 未确认网络已安全停用");
        }

        log.warn("删除 Tinc 网络管理记录但保留物理配置: id={}, netName={}", id, network.getNetworkName());
    }

    @Override
    public List<Long> getAvailablePorts(){
        List<Long> ports = new ArrayList<>();
        List<MangeServer> serverList = mangeServerService.selectMangeServerList(new MangeServer());
        for(MangeServer server:serverList){
            Long startPort = server.getStartPort();
            Long endPort = server.getEndPort();
            if(startPort != null && endPort != null){
                for(Long port = startPort; port <= endPort; port++){
                    ports.add(port);
                }
            }
        }
        return ports;
    }

    @Override
    public List<String> getAvailableSegments(){
        List<String> segments = new ArrayList<>();
        List<MangeServer> serverList = mangeServerService.selectMangeServerList(new MangeServer());
        for(MangeServer server:serverList){
            String startSegment = server.getStartSegment();
            String endSegment  = server.getEndSegment();
            if(startSegment != null && endSegment != null){
                segments.add(startSegment);
                segments.add(endSegment);
            }
        }
        return segments;
    }

    private MangeServer requireServer(Long serverId)
    {
        if (serverId == null) {
            throw new IllegalArgumentException("请选择有效的接入服务器 ID");
        }
        MangeServer server = mangeServerService.selectMangeServerById(serverId);
        if (server == null) {
            throw new IllegalArgumentException("接入服务器不存在: id=" + serverId);
        }
        return server;
    }

    private void validateNetworkResources(TincNetworkMange network, MangeServer server)
    {
        if (tincNetworkMangeMapper.countNetworkNameExcludingId(
                network.getServerId(), network.getNetworkName(), network.getId()) > 0) {
            throw new IllegalStateException("TINC_NETWORK_NAME_CONFLICT: 网络名称已存在");
        }
        int port = Integer.parseInt(network.getPort());
        if (server.getStartPort() != null && server.getEndPort() != null
                && (port < server.getStartPort() || port > server.getEndPort())) {
            throw new IllegalStateException("TINC_PORT_OUT_OF_RANGE: 端口超出所选 Access Server 的资源范围");
        }
        if (tincNetworkMangeMapper.countPortExcludingId(
                network.getServerId(), network.getPort(), network.getId()) > 0) {
            throw new IllegalStateException("TINC_PORT_CONFLICT: 同一 Access Server 内端口必须唯一");
        }
        validateSegmentRange(network.getSegment(), server);
        List<TincNetworkMange> networks = tincNetworkMangeMapper.selectByServerIdForConflictCheck(network.getServerId());
        if (networks != null) {
            for (TincNetworkMange existing : networks) {
                if (network.getId() != null && network.getId().equals(existing.getId())) {
                    continue;
                }
                // 当前模型固定分配 /24；前三段相同即为相同或重叠。
                if (network.getSegment().equals(existing.getSegment())) {
                    throw new IllegalStateException("TINC_SEGMENT_CONFLICT: 同一 Access Server 内网段不能重复或重叠");
                }
            }
        }
    }

    private void validateSegmentRange(String segment, MangeServer server)
    {
        if (server.getStartSegment() == null || server.getEndSegment() == null
                || server.getStartSegment().isEmpty() || server.getEndSegment().isEmpty()) return;
        long value = segmentValue(segment);
        long start = segmentValue(TincModelValidator.requireSegment(server.getStartSegment()));
        long end = segmentValue(TincModelValidator.requireSegment(server.getEndSegment()));
        if (start > end || value < start || value > end) {
            throw new IllegalStateException("TINC_SEGMENT_OUT_OF_RANGE: 网段超出所选 Access Server 的资源范围");
        }
    }

    private long segmentValue(String segment)
    {
        String[] parts = segment.split("\\.");
        return (Long.parseLong(parts[0]) << 16) | (Long.parseLong(parts[1]) << 8) | Long.parseLong(parts[2]);
    }
}

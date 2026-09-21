package com.ruoyi.tinc_server.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.tinc_server.mapper.MangeServerMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.tinc.runtime.AccessAgentClient;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务器集群管理Service业务层处理
 * 
 * @author sun
 * @date 2025-12-18
 */
@Service
public class MangeServerServiceImpl implements IMangeServerService 
{
    private static final Logger log = LoggerFactory.getLogger(MangeServerServiceImpl.class);
    // 注入服务器集群管理Mapper
    // 动态代理，将Mapper接口的方法映射到XML文件中的SQL语句（创建对象）
    @Autowired
    private MangeServerMapper mangeServerMapper;

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    @Autowired(required = false)
    private AccessAgentClient accessAgentClient;

    /**
     * 查询服务器集群管理
     * 
     * @param Id 服务器集群管理主键
     * @return 服务器集群管理
     */
    @Override
    public MangeServer selectMangeServerById(Long Id)
    {
        return mangeServerMapper.selectMangeServerById(Id);
    }

    /**
     * 查询服务器集群管理列表
     * 
     * @param mangeServer 服务器集群管理
     * @return 服务器集群管理
     */
    @Override
    public List<MangeServer> selectMangeServerList(MangeServer mangeServer)
    {
        return mangeServerMapper.selectMangeServerList(mangeServer);
    }

    @Override
    public MangeServer selectByServerNameExact(String serverName)
    {
        List<MangeServer> matches = mangeServerMapper.selectByServerNameExact(serverName);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("服务器名称存在重复记录，无法建立稳定关联");
        }
        return matches.get(0);
    }

    /**
     * 新增服务器集群管理
     * 
     * @param mangeServer 服务器集群管理
     * @return 结果
     */
    @Override
    //增加服务器
    public int insertMangeServer(MangeServer mangeServer)
    {
        normalizeAndValidateRuntime(mangeServer, null);
        // 校验服务器名字是否唯一
        MangeServer serverByName = mangeServerMapper.checkServerNameUnique(mangeServer);
        if(serverByName != null)
        {
            throw new RuntimeException("服务器名字已存在");
        }
        MangeServer serverByIp = mangeServerMapper.checkServerIpUnique(mangeServer);
        if(serverByIp != null)
        {
            throw new RuntimeException("服务器IP已存在");
        }

        int rows = mangeServerMapper.insertMangeServer(mangeServer);
        if (TincRuntimeRouter.RUNTIME_AGENT.equals(mangeServer.getRuntimeType()) && accessAgentClient != null) {
            probeAgent(mangeServer.getId());
        }
        return rows;
    }

    /**
     * 修改服务器集群管理
     * 
     * @param mangeServer 服务器集群管理
     * @return 结果
     */
    @Override
    //修改服务器
    public int updateMangeServer(MangeServer mangeServer)
    {
        MangeServer existing = mangeServerMapper.selectMangeServerById(mangeServer.getId());
        if (existing == null) {
            throw new IllegalArgumentException("服务器不存在");
        }
        if (mangeServer.getServerName() != null
                && !existing.getServerName().equals(mangeServer.getServerName())) {
            throw new IllegalStateException("TINC_SERVER_RENAME_FORBIDDEN: 第一阶段禁止修改服务器名称");
        }
        mangeServer.setServerName(existing.getServerName());
        normalizeAndValidateRuntime(mangeServer, existing);
        // 校验服务器名字是否唯一
        MangeServer serverByName = mangeServerMapper.checkServerNameUnique(mangeServer);
        if(serverByName != null && !serverByName.getId().equals(mangeServer.getId()))
        {
            throw new RuntimeException("服务器名字已存在");
        }
        MangeServer serverByIp = mangeServerMapper.checkServerIpUnique(mangeServer);
        if(serverByIp != null && !serverByIp.getId().equals(mangeServer.getId()))
        {
            throw new RuntimeException("服务器IP已存在");
        }
        int rows = mangeServerMapper.updateMangeServer(mangeServer);
        if (TincRuntimeRouter.RUNTIME_AGENT.equals(mangeServer.getRuntimeType()) && accessAgentClient != null) {
            probeAgent(mangeServer.getId());
        }
        return rows;
    }

    /**
     * 批量删除服务器集群管理
     * 
     * @param Ids 需要删除的服务器集群管理主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMangeServerByIds(Long[] Ids)
    {
        if (Ids == null || Ids.length == 0) {
            return 0;
        }
        for (Long id : Ids) {
            validateServerDeletion(id);
        }
        return mangeServerMapper.deleteMangeServerByIds(Ids);
    }

    /**
     * 删除服务器集群管理信息
     * 
     * @param Id 服务器集群管理主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMangeServerById(Long Id)
    {
        validateServerDeletion(Id);
        return mangeServerMapper.deleteMangeServerById(Id);
    }

    private void validateServerDeletion(Long id)
    {
        MangeServer server = mangeServerMapper.selectMangeServerByIdForUpdate(id);
        if (server == null) {
            return;
        }

        if (tincNetworkMangeMapper.countByServerId(id) > 0) {
            throw new IllegalStateException("TINC_SERVER_IN_USE: 服务器 [" + server.getServerName()
                    + "] 仍有关联网络，不能删除");
        }
    }

    @Override
    public MangeServer probeAgent(Long id)
    {
        MangeServer server = mangeServerMapper.selectMangeServerById(id);
        if (server == null) return null;
        if (!TincRuntimeRouter.RUNTIME_AGENT.equalsIgnoreCase(server.getRuntimeType())) {
            server.setAgentStatus("LOCAL");
            return server;
        }
        try {
            AccessAgentHealth health = accessAgentClient.health(server);
            String state = health != null && "UP".equals(health.getStatus()) ? "ONLINE" : "DEGRADED";
            server.setAgentStatus(state);
            server.setAgentVersion(health == null ? null : health.getAgentVersion());
            server.setAgentId(health == null ? null : health.getAgentId());
            server.setAgentCpuUsage(health == null ? null : health.getCpuUsage());
            server.setAgentMemoryUsage(health == null ? null : health.getMemoryUsage());
            server.setAgentNetworkCount(health == null ? null : health.getNetworkCount());
            server.setAgentLastSeen(DateUtils.getNowDate());
            server.setStatus("ONLINE".equals(state) ? 1L : 0L);
        } catch (RuntimeException e) {
            server.setAgentStatus("UNREACHABLE");
            server.setStatus(0L);
            log.warn("Access Agent 探针失败: serverId={}, errorType={}", id, e.getClass().getSimpleName());
        }
        mangeServerMapper.updateAgentHealth(server);
        return server;
    }

    @Override
    public void pollAgentHealth()
    {
        if (accessAgentClient == null) return;
        MangeServer filter = new MangeServer();
        filter.setRuntimeType(TincRuntimeRouter.RUNTIME_AGENT);
        List<MangeServer> servers = mangeServerMapper.selectMangeServerList(filter);
        if (servers == null) return;
        for (MangeServer server : servers) probeAgent(server.getId());
    }

    private void normalizeAndValidateRuntime(MangeServer server, MangeServer existing)
    {
        String runtimeType = server.getRuntimeType();
        if (runtimeType == null || runtimeType.trim().isEmpty()) {
            runtimeType = existing == null || existing.getRuntimeType() == null
                    ? TincRuntimeRouter.RUNTIME_LOCAL : existing.getRuntimeType();
        }
        runtimeType = runtimeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!TincRuntimeRouter.RUNTIME_LOCAL.equals(runtimeType)
                && !TincRuntimeRouter.RUNTIME_AGENT.equals(runtimeType)) {
            throw new IllegalArgumentException("运行模式仅允许 LOCAL 或 AGENT");
        }
        server.setRuntimeType(runtimeType);
        if (server.getStartPort() != null && server.getEndPort() != null
                && (server.getStartPort() < 1 || server.getEndPort() > 65535
                || server.getStartPort() > server.getEndPort())) {
            throw new IllegalArgumentException("端口资源范围无效");
        }
        if (TincRuntimeRouter.RUNTIME_AGENT.equals(runtimeType)) {
            if (server.getAgentPort() == null && existing != null) server.setAgentPort(existing.getAgentPort());
            if (server.getAgentPort() == null || server.getAgentPort() < 1 || server.getAgentPort() > 65535) {
                throw new IllegalArgumentException("Agent Port 必须在 1-65535 之间");
            }
            if ((server.getAgentSecret() == null || server.getAgentSecret().isEmpty()) && existing != null) {
                server.setAgentSecret(existing.getAgentSecret());
            }
            if (server.getAgentSecret() == null || server.getAgentSecret().length() < 32
                    || server.getAgentSecret().length() > 512) {
                throw new IllegalArgumentException("Agent Secret 长度必须为 32-512");
            }
            if (existing == null) server.setAgentStatus("UNREACHABLE");
        } else {
            server.setAgentStatus("LOCAL");
        }
    }
}

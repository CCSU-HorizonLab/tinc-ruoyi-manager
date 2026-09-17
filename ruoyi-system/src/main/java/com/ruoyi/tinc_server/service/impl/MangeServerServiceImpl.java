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

/**
 * 服务器集群管理Service业务层处理
 * 
 * @author sun
 * @date 2025-12-18
 */
@Service
public class MangeServerServiceImpl implements IMangeServerService 
{
    // 注入服务器集群管理Mapper
    // 动态代理，将Mapper接口的方法映射到XML文件中的SQL语句（创建对象）
    @Autowired
    private MangeServerMapper mangeServerMapper;

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

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

        return mangeServerMapper.insertMangeServer(mangeServer);
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
        return mangeServerMapper.updateMangeServer(mangeServer);
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
        MangeServer server = mangeServerMapper.selectMangeServerById(id);
        if (server == null) {
            return;
        }

        TincNetworkMange networkQuery = new TincNetworkMange();
        networkQuery.setServerName(server.getServerName());
        List<TincNetworkMange> networks = tincNetworkMangeMapper.selectTincNetworkMangeList(networkQuery);
        if (networks != null && !networks.isEmpty()) {
            throw new IllegalStateException("TINC_SERVER_IN_USE: 服务器 [" + server.getServerName()
                    + "] 仍有关联网络，不能删除");
        }

        TincNodeMange nodeQuery = new TincNodeMange();
        nodeQuery.setServerName(server.getServerName());
        List<TincNodeMange> nodes = tincNodeMangeMapper.selectTincNodeMangeList(nodeQuery);
        if (nodes != null && !nodes.isEmpty()) {
            throw new IllegalStateException("TINC_SERVER_IN_USE: 服务器 [" + server.getServerName()
                    + "] 仍有关联节点，不能删除");
        }
    }
}

package com.ruoyi.tinc_server.service;

import java.util.List;
import com.ruoyi.tinc_server.domain.MangeServer;

/**
 * 服务器集群管理Service接口
 * 
 * @author sun
 * @date 2025-12-18
 */
public interface IMangeServerService 
{
    /**
     * 查询服务器集群管理
     * 
     * @param Id 服务器集群管理主键
     * @return 服务器集群管理
     */
    public MangeServer selectMangeServerById(Long Id);

    /**
     * 查询服务器集群管理列表
     * 
     * @param mangeServer 服务器集群管理
     * @return 服务器集群管理集合
     */
    public List<MangeServer> selectMangeServerList(MangeServer mangeServer);

    /**
     * 新增服务器集群管理
     * 
     * @param mangeServer 服务器集群管理
     * @return 结果
     */
    public int insertMangeServer(MangeServer mangeServer);

    /**
     * 修改服务器集群管理
     * 
     * @param mangeServer 服务器集群管理
     * @return 结果
     */
    public int updateMangeServer(MangeServer mangeServer);

    /**
     * 批量删除服务器集群管理
     * 
     * @param Ids 需要删除的服务器集群管理主键集合
     * @return 结果
     */
    public int deleteMangeServerByIds(Long[] Ids);

    /**
     * 删除服务器集群管理信息
     * 
     * @param Id 服务器集群管理主键
     * @return 结果
     */
    public int deleteMangeServerById(Long Id);
}

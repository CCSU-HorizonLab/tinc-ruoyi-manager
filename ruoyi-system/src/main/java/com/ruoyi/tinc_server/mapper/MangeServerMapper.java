package com.ruoyi.tinc_server.mapper;

import java.util.List;
import com.ruoyi.tinc_server.domain.MangeServer;

/**
 * 服务器集群管理Mapper接口
 * 
 * @author sun
 * @date 2025-12-18
 */
public interface MangeServerMapper 
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
     * 删除服务器集群管理
     * 
     * @param Id 服务器集群管理主键
     * @return 结果
     */
    public int deleteMangeServerById(Long Id);
    
    /**
     * 检查服务器名字是否唯一
     * 
     * @param mangeServer 服务器名字主键
     * @return 结果
     */
    //检查服务器名字是否唯一
    public MangeServer checkServerNameUnique(MangeServer mangeServer);

    /**
     * 检查服务器ip是否唯一
     * 
     * @param mangeServer 服务器ip主键
     * @return 结果
     */
    //检查服务器ip是否唯一
    public MangeServer checkServerIpUnique(MangeServer mangeServer);


    /**
     * 批量删除服务器集群管理
     * 
     * @param Ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteMangeServerByIds(Long[] Ids);
}

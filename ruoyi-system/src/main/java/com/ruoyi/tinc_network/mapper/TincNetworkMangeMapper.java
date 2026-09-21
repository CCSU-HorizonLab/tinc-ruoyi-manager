package com.ruoyi.tinc_network.mapper;

import java.util.List;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import org.apache.ibatis.annotations.Param;

/**
 * Tinc内网集群管理Mapper接口
 * 
 * @author sun
 * @date 2025-12-20
 */
public interface TincNetworkMangeMapper 
{
    /**
     * 查询Tinc内网集群管理
     * 根据主键ID查询单个Tinc内网集群的详细配置信息
     * 支持前端编辑和查看详情功能
     * 
     * @param id Tinc内网集群管理主键
     * @return Tinc内网集群管理对象
     */
    public TincNetworkMange selectTincNetworkMangeById(Long id);

    public TincNetworkMange selectTincNetworkMangeByIdForUpdate(@Param("id") Long id);

    /**
     * 查询Tinc内网集群管理列表
     * 根据查询条件查询Tinc内网集群管理的集合列表
     * 支持前端表格数据展示和筛选功能
     * 
     * @param tincNetworkMange Tinc内网集群管理查询条件
     * @return Tinc内网集群管理集合列表
     */
    public List<TincNetworkMange> selectTincNetworkMangeList(TincNetworkMange tincNetworkMange);

    public List<TincNetworkMange> selectByNetworkNameExact(@Param("networkName") String networkName);

    public int countByServerId(@Param("serverId") Long serverId);

    public int countNetworkNameExcludingId(@Param("serverId") Long serverId,
                                           @Param("networkName") String networkName,
                                           @Param("excludeId") Long excludeId);

    public int countPortExcludingId(@Param("serverId") Long serverId,
                                    @Param("port") String port, @Param("excludeId") Long excludeId);

    public List<TincNetworkMange> selectByServerIdForConflictCheck(@Param("serverId") Long serverId);

    /** 当前单 Runtime 下把 Network 的全局唯一性检查串行化。 */
    public List<Long> lockAllServerIdsForNetworkMutation();

    /**
     * 新增Tinc内网集群管理
     * 将新的Tinc内网集群配置信息插入到数据库中
     * 包括服务器ID、端口、网段等配置
     * 
     * @param tincNetworkMange Tinc内网集群管理信息
     * @return 操作结果（影响行数）
     */
    public int insertTincNetworkMange(TincNetworkMange tincNetworkMange);

    /**
     * 修改Tinc内网集群管理
     * 更新数据库中已有的Tinc内网集群配置信息
     * 可修改服务器、端口、网段等配置
     * 
     * @param tincNetworkMange Tinc内网集群管理信息
     * @return 操作结果（影响行数）
     */
    public int updateTincNetworkMange(TincNetworkMange tincNetworkMange);

    /**
     * 删除Tinc内网集群管理
     * 根据主键ID删除单个Tinc内网集群配置信息
     * 
     * @param id Tinc内网集群管理主键
     * @return 操作结果（影响行数）
     */
    public int deleteTincNetworkMangeById(Long id);

    /**
     * 批量删除Tinc内网集群管理
     * 根据主键ID数组批量删除Tinc内网集群配置信息
     * 
     * @param ids 需要删除的数据主键集合
     * @return 操作结果（影响行数）
     */
    public int deleteTincNetworkMangeByIds(Long[] ids);
    /**
     * 校验端口唯一性
     */
    public int checkPortUnique(String port);

    /**
     * 校验网段唯一性
     */
    public int checkSegmentUnique(String segment);
}

package com.ruoyi.tinc_network.service;

import java.util.List;
import com.ruoyi.tinc_network.domain.TincNetworkMange;

/**
 * Tinc内网集群管理Service接口
 * 
 * @author sun
 * @date 2025-12-20
 */
public interface ITincNetworkMangeService 
{
    /**
     * 查询Tinc内网集群管理
     * 根据ID获取单个Tinc内网集群的详细信息
     * 用于编辑加载数据和查看详情
     * 
     * @param id Tinc内网集群管理主键
     * @return Tinc内网集群管理对象
     */
    public TincNetworkMange selectTincNetworkMangeById(Long id);

    /**
     * 查询Tinc内网集群管理列表
     * 根据查询条件获取Tinc内网集群管理列表数据
     * 为前端表格提供数据来源
     * 
     * @param tincNetworkMange Tinc内网集群管理查询条件
     * @return Tinc内网集群管理集合列表
     */
    public List<TincNetworkMange> selectTincNetworkMangeList(TincNetworkMange tincNetworkMange);

    /**
     * 新增Tinc内网集群管理
     * 添加新的Tinc内网集群配置信息
     * 包括服务器选择、端口分配、网段配置等
     * 
     * @param tincNetworkMange Tinc内网集群管理信息
     * @return 操作结果（影响行数）
     */
    public int insertTincNetworkMange(TincNetworkMange tincNetworkMange);

    /**
     * 修改Tinc内网集群管理
     * 更新已存在的Tinc内网集群配置信息
     * 可修改服务器、端口、网段等配置
     * 
     * @param tincNetworkMange Tinc内网集群管理信息
     * @return 操作结果（影响行数）
     */
    public int updateTincNetworkMange(TincNetworkMange tincNetworkMange);

    /**
     * 批量删除Tinc内网集群管理
     * 根据ID数组批量删除Tinc内网集群配置信息
     * 
     * @param ids 需要删除的Tinc内网集群管理主键集合
     * @return 操作结果（影响行数）
     */
    public int deleteTincNetworkMangeByIds(Long[] ids);

    /**
     * 删除Tinc内网集群管理信息
     * 根据单个ID删除Tinc内网集群配置信息
     * 
     * @param id Tinc内网集群管理主键
     * @return 操作结果（影响行数）
     */
    public int deleteTincNetworkMangeById(Long id);

    /**
     * 获取可用端口列表
     * 为前端端口选项框提供数据来源
     * @return 可用端口范围列表
     */
   public List<Long> getAvailablePorts();
   /**
    * 获取可用网段列表
    * 为前端网段选项框提供数据来源
    * @return 可用网段范围列表
    */
   public List<String> getAvailableSegments();
}

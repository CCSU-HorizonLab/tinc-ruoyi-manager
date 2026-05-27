package com.ruoyi.tinc_node.service;

import java.util.List;
import com.ruoyi.tinc_node.domain.TincNodeMange;

/**
 * Tinc节点集群管理Service接口
 * 
 * @author sun
 * @date 2025-12-22
 */
public interface ITincNodeMangeService 
{
    /**
     * 查询Tinc节点集群管理
     * 
     * @param id Tinc节点集群管理主键
     * @return Tinc节点集群管理
     */
    public TincNodeMange selectTincNodeMangeById(Long id);

    /**
     * 查询Tinc节点集群管理列表
     * 
     * @param tincNodeMange Tinc节点集群管理
     * @return Tinc节点集群管理集合
     */
    public List<TincNodeMange> selectTincNodeMangeList(TincNodeMange tincNodeMange);

    /**
     * 新增Tinc节点集群管理
     * 
     * @param tincNodeMange Tinc节点集群管理
     * @return 结果
     */
    public int insertTincNodeMange(TincNodeMange tincNodeMange);

    /**
     * 修改Tinc节点集群管理
     * 
     * @param tincNodeMange Tinc节点集群管理
     * @return 结果
     */
    public int updateTincNodeMange(TincNodeMange tincNodeMange);

    /**
     * 批量删除Tinc节点集群管理
     * 
     * @param ids 需要删除的Tinc节点集群管理主键集合
     * @return 结果
     */
    public int deleteTincNodeMangeByIds(Long[] ids);

    /**
     * 删除Tinc节点集群管理信息
     * 
     * @param id Tinc节点集群管理主键
     * @return 结果
     */
    public int deleteTincNodeMangeById(Long id);
}

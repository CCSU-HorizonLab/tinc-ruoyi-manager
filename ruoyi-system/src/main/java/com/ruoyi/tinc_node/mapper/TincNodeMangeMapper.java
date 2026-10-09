package com.ruoyi.tinc_node.mapper;

import java.util.List;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import org.apache.ibatis.annotations.Param;

/**
 * Tinc节点集群管理Mapper接口
 * 
 * @author sun
 * @date 2025-12-22
 */
public interface TincNodeMangeMapper 
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

    public List<TincNodeMange> selectByNodeNameExact(@Param("nodeName") String nodeName);

    public List<TincNodeMange> selectByNetworkId(@Param("networkId") Long networkId);

    public int countByNetworkId(@Param("networkId") Long networkId);

    public int countNodeNameInNetwork(@Param("networkId") Long networkId,
                                      @Param("nodeName") String nodeName,
                                      @Param("excludeId") Long excludeId);

    public int countNetworkIpInNetwork(@Param("networkId") Long networkId,
                                       @Param("networkIp") String networkIp,
                                       @Param("excludeId") Long excludeId);

    /**
     * 根据节点接入码精确查询唯一节点（登录身份唯一定位）
     */
    public TincNodeMange selectByAccessCode(@Param("accessCode") String accessCode);

    /**
     * 检查接入码是否在系统中已被占用
     */
    public int countByAccessCode(@Param("accessCode") String accessCode,
                                 @Param("excludeId") Long excludeId);

    /**
     * 单独轮换节点的接入码
     */
    public int updateAccessCode(@Param("id") Long id,
                                @Param("accessCode") String accessCode,
                                @Param("updateTime") java.util.Date updateTime);

    /** Upgrade legacy plaintext only if the credential has not changed. */
    public int upgradeLegacyPassword(@Param("id") Long id,
                                     @Param("expectedFingerprint") String expectedFingerprint,
                                     @Param("encodedPassword") String encodedPassword);

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
     * 删除Tinc节点集群管理
     * 
     * @param id Tinc节点集群管理主键
     * @return 结果
     */
    public int deleteTincNodeMangeById(Long id);

    /**
     * 批量删除Tinc节点集群管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteTincNodeMangeByIds(Long[] ids);
}

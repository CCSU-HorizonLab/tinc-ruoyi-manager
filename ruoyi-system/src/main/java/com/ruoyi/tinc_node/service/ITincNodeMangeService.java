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

    /** 客户端登录兼容入口：数据库精确匹配节点名，允许跨网络同名。 */
    public List<TincNodeMange> selectByNodeNameExact(String nodeName);

    /**
     * 根据全局唯一接入码精确查询节点（登录前唯一定位 Node）
     *
     * @param accessCode 节点接入码
     * @return 节点信息（未找到返回 null）
     */
    public TincNodeMange selectByAccessCode(String accessCode);

    /**
     * 轮换指定节点的接入码（生成新唯一接入码，旧码立即失效；不影响 nodeId/networkId/KeyPair/Runtime）
     *
     * @param nodeId 节点 ID
     * @return 新生成的接入码
     */
    public String rotateAccessCode(Long nodeId);

    /** Authenticate a node and upgrade unchanged legacy plaintext on success. */
    public boolean verifyClientPassword(TincNodeMange node, String rawPassword);

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

    /** 公钥应用成功后仅标记配置状态，不修改身份、密码或撤销会话。 */
    public int markClientConfigured(Long nodeId);

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

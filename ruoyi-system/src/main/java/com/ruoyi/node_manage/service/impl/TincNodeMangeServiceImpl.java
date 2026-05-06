package com.ruoyi.node_manage.service.impl;

import java.util.List;

import com.ruoyi.common.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.node_manage.mapper.TincNodeMangeMapper;
import com.ruoyi.node_manage.domain.TincNodeMange;
import com.ruoyi.node_manage.service.ITincNodeMangeService;

/**
 * Tinc节点集群管理Service业务层处理
 */
@Service
public class  TincNodeMangeServiceImpl implements ITincNodeMangeService
{
    private static final Logger log = LoggerFactory.getLogger(TincNodeMangeServiceImpl.class);

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    // 查询方法保持不变...
    @Override
    public TincNodeMange selectTincNodeMangeById(Long id) {
        return tincNodeMangeMapper.selectTincNodeMangeById(id);
    }

    @Override
    public List<TincNodeMange> selectTincNodeMangeList(TincNodeMange tincNodeMange) {
        return tincNodeMangeMapper.selectTincNodeMangeList(tincNodeMange);
    }

    /**
     * 新增Tinc节点集群管理 (适配 Qt 客户端)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTincNodeMange(TincNodeMange tincNodeMange)
    {
        // 1. 设置基础数据
        tincNodeMange.setCreateTime(DateUtils.getNowDate());

        // 可选：设置初始状态为“等待配置”或“离线”
        // tincNodeMange.setStatus("待配置");
        // tincNodeMange.setNodeStatus("离线");

        // 2. 仅执行数据库插入，获取 ID
        int rows = tincNodeMangeMapper.insertTincNodeMange(tincNodeMange);

        // 删除了所有生成私钥、生成配置文件、打包 ZIP 的冗余逻辑！
        // 因为这些全交由 Qt 客户端通过 API 交互来完成了！
        log.info("节点 [{}] 基础信息创建成功，等待 Qt 客户端登录并上传公钥...", tincNodeMange.getNodeName());

        return rows;
    }

    // update, delete 等其他方法保持不变...
    @Override
    public int updateTincNodeMange(TincNodeMange tincNodeMange) {
        return tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
    }

    @Override
    public int deleteTincNodeMangeByIds(Long[] ids) {
        return tincNodeMangeMapper.deleteTincNodeMangeByIds(ids);
    }

    @Override
    public int deleteTincNodeMangeById(Long id) {
        return tincNodeMangeMapper.deleteTincNodeMangeById(id);
    }
}
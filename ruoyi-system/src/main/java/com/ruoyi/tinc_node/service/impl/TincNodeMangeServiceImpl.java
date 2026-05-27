package com.ruoyi.tinc_node.service.impl;

import java.util.List;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;

/**
 * Tinc节点集群管理Service业务层处理
 */
@Service
public class TincNodeMangeServiceImpl implements ITincNodeMangeService
{
    private static final Logger log = LoggerFactory.getLogger(TincNodeMangeServiceImpl.class);

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    @Override
    public TincNodeMange selectTincNodeMangeById(Long id) {
        return tincNodeMangeMapper.selectTincNodeMangeById(id);
    }

    @Override
    public List<TincNodeMange> selectTincNodeMangeList(TincNodeMange tincNodeMange) {
        return tincNodeMangeMapper.selectTincNodeMangeList(tincNodeMange);
    }

    /**
     * 新增 Tinc 节点（仅入库，密钥由 Qt 客户端自行生成并通过 API 上传）
     */
    @Override
    public int insertTincNodeMange(TincNodeMange tincNodeMange) {
        tincNodeMange.setCreateTime(DateUtils.getNowDate());
        int rows = tincNodeMangeMapper.insertTincNodeMange(tincNodeMange);
        log.info("节点 [{}] 基础信息创建成功，等待客户端上传公钥...", tincNodeMange.getNodeName());
        return rows;
    }

    /**
     * 修改 Tinc 节点（兼容两种场景）：
     *
     * 场景 A — 客户端手动上传公钥：
     *   前端把脏公钥文本塞进 password 字段传来，本方法用正则抠出纯净 PEM 块，
     *   覆写服务端 hosts 文件，然后仅更新状态为"已配置"。
     *
     * 场景 B — 普通字段更新（改状态/改备注等）：
     *   password 字段不包含 PEM 公钥标记，直接透传给 Mapper 做普通 update。
     */
    @Override
    public int updateTincNodeMange(TincNodeMange tincNodeMange) {
        String dirtyText = tincNodeMange.getPassword();

        // 判断是否为公钥上传场景：password 字段里是否包含 PEM 公钥头尾
        if (dirtyText != null && dirtyText.contains("-----BEGIN") && dirtyText.contains("PUBLIC KEY-----")) {

            // 1. 查出旧节点信息（获取 netName, nodeName, 旧的 IP）
            TincNodeMange oldNode = tincNodeMangeMapper.selectTincNodeMangeById(tincNodeMange.getId());
            if (oldNode == null) {
                throw new RuntimeException("节点不存在");
            }

            String netName  = oldNode.getNetworkName();
            String nodeName = oldNode.getNodeName();
            String rawIp    = oldNode.getNetworkIp();
            String nodeIp   = (rawIp != null && rawIp.contains("/")) ? rawIp : rawIp + "/32";

            // 2. 正则精准抠出纯净公钥块
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?s)(-----BEGIN(?: RSA)? PUBLIC KEY-----.*?-----END(?: RSA)? PUBLIC KEY-----)");
            java.util.regex.Matcher matcher = pattern.matcher(dirtyText);

            if (!matcher.find()) {
                throw new RuntimeException("未识别到有效的公钥格式，请检查上传内容");
            }
            String cleanPubKey = matcher.group(0);

            // 3. 强行覆写服务端 hosts 目录（完全覆盖，杜绝 Subnet 叠罗汉）
            TincConfigUtils.createHostFile(netName, nodeName, nodeIp, cleanPubKey);

            // 4. 仅更新业务状态，绝不将公钥写入数据库
            tincNodeMange.setPassword(null);
            tincNodeMange.setStatus("已配置");
            int rows = tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
            log.info("节点 [{}] 公钥已覆写至服务端 hosts 目录", nodeName);
            return rows;
        }

        // 场景 B：普通更新，直接透传
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

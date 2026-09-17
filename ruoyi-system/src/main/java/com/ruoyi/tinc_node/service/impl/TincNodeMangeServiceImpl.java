package com.ruoyi.tinc_node.service.impl;

import java.util.List;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private IMangeServerService mangeServerService;

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
     * <p>场景 A — 客户端手动上传公钥：</p>
     * <ol>
     *   <li>前端把脏公钥文本塞进 password 字段</li>
     *   <li>正则抠出纯净 PEM 块</li>
     *   <li>覆写本地 hosts 文件副本</li>
     *   <li>★ 推送到远程 Tinc VPN 网关</li>
     *   <li>★ 远程重载 tincd 使新节点公钥生效</li>
     * </ol>
     *
     * <p>场景 B — 普通字段更新（改状态/改备注等）：</p>
     * <ul>
     *   <li>password 字段不包含 PEM 公钥标记，直接透传给 Mapper</li>
     * </ul>
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

            // ★ 查出该网络对应的网关 IP
            String gatewayIp = getGatewayIpForNetwork(netName);

            // 2. 正则精准抠出纯净公钥块
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?s)(-----BEGIN(?: RSA)? PUBLIC KEY-----.*?-----END(?: RSA)? PUBLIC KEY-----)");
            java.util.regex.Matcher matcher = pattern.matcher(dirtyText);

            if (!matcher.find()) {
                throw new RuntimeException("未识别到有效的公钥格式，请检查上传内容");
            }
            String cleanPubKey = matcher.group(0);

            // 3. 覆写本地 hosts + 推送远程网关
            TincConfigUtils.createHostFile(gatewayIp, netName, nodeName, nodeIp, cleanPubKey);

            // 4. ★ 远程重载 tincd 使新节点公钥生效
            TincConfigUtils.reloadGatewayTinc(gatewayIp, netName);

            // 5. 仅更新业务状态，绝不将公钥写入数据库
            tincNodeMange.setPassword(null);
            tincNodeMange.setStatus("已配置");
            int rows = tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
        log.info("节点 [{}] 公钥已推送至对应网关的 hosts 目录", nodeName);
            return rows;
        }

        // 场景 B：普通更新，直接透传
        return tincNodeMangeMapper.updateTincNodeMange(tincNodeMange);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNodeMangeByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            TincNodeMange node = tincNodeMangeMapper.selectTincNodeMangeById(id);
            if (node != null) {
                log.warn("删除 Tinc 节点管理记录但保留网关 hosts 配置: id={}, nodeName={}, netName={}",
                        id, node.getNodeName(), node.getNetworkName());
            }
        }
        return tincNodeMangeMapper.deleteTincNodeMangeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNodeMangeById(Long id) {
        TincNodeMange node = tincNodeMangeMapper.selectTincNodeMangeById(id);
        if (node != null) {
            log.warn("删除 Tinc 节点管理记录但保留网关 hosts 配置: id={}, nodeName={}, netName={}",
                    id, node.getNodeName(), node.getNetworkName());
        }
        return tincNodeMangeMapper.deleteTincNodeMangeById(id);
    }

    /**
     * 根据网络名称查询对应的网关服务器 IP
     * <p>
     * 链路：tinc_node → tinc_network → server_name → mange_server → server_ip
     * </p>
     */
    private String getGatewayIpForNetwork(String networkName) {
        TincNetworkMange queryNetwork = new TincNetworkMange();
        queryNetwork.setNetworkName(networkName);
        List<TincNetworkMange> networks = tincNetworkMangeMapper.selectTincNetworkMangeList(queryNetwork);
        if (networks == null || networks.isEmpty()) {
            throw new RuntimeException("网络不存在: " + networkName);
        }

        String serverName = networks.get(0).getServerName();
        MangeServer queryServer = new MangeServer();
        queryServer.setServerName(serverName);
        List<MangeServer> servers = mangeServerService.selectMangeServerList(queryServer);
        if (servers == null || servers.isEmpty()) {
            throw new RuntimeException("服务器不存在: " + serverName);
        }

        return servers.get(0).getServerIp();
    }
}

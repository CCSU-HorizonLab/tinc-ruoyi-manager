package com.ruoyi.tinc_network.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.RsaUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;

import com.ruoyi.tinc_server.service.*;
import com.ruoyi.tinc_server.domain.*;

/**
 * Tinc内网集群管理Service业务层处理
 *
 * @author sun
 */
@Service
public class TincNetworkMangeServiceImpl implements ITincNetworkMangeService
{
    private static final Logger log = LoggerFactory.getLogger(TincNetworkMangeServiceImpl.class);

    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private IMangeServerService mangeServerService;

    @Autowired
    private TincRuntimeManager tincRuntimeManager;

    @Autowired
    private TincNodeMangeMapper tincNodeMangeMapper;

    @Override
    public TincNetworkMange selectTincNetworkMangeById(Long id)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeById(id);
    }

    @Override
    public List<TincNetworkMange> selectTincNetworkMangeList(TincNetworkMange tincNetworkMange)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeList(tincNetworkMange);
    }

    /**
     * 新增Tinc内网集群管理 (核心改造 — 支持远程推送)
     * <p>
     * 1. 写入数据库
     * 2. 根据所选服务器查出网关真实 IP
     * 3. 在本地生成配置文件副本
     * 4. 通过 SSH/SCP 推送到远程 Tinc VPN 网关
     * 5. 推送完成后远程重载 tincd
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTincNetworkMange(TincNetworkMange network)
    {
        // 1. 设置基础信息并入库
        network.setCreateTime(DateUtils.getNowDate());
        network.setNetworkStatus("初始化中");
        int rows = tincNetworkMangeMapper.insertTincNetworkMange(network);

        // 2. 生成配置 → 本地副本 + 远程推送
        try {
            String netName = network.getNetworkName();

            // 根据所选服务器名称查出真实公网 IP
            String selectedServerName = network.getServerName();
            if (selectedServerName == null || selectedServerName.isEmpty()) {
                throw new RuntimeException("请选择接入服务器！");
            }

            MangeServer query = new MangeServer();
            query.setServerName(selectedServerName);
            List<MangeServer> serverList = mangeServerService.selectMangeServerList(query);

            if (serverList == null || serverList.isEmpty()) {
                throw new RuntimeException("系统里找不到名为 [" + selectedServerName + "] 的服务器，请检查服务器集群管理！");
            }

            // ★ 关键：网关真实 IP，后续所有推送都基于这个 IP
            String gatewayIp = serverList.get(0).getServerIp();

            // A. 初始化目录（本地 + 触发远程 mkdirs）
            TincConfigUtils.initNetworkEnv(gatewayIp, netName);

            // B. 生成 4096位 密钥
            Map<String, String> keyMap = RsaUtils.generateKeys();

            // C. 生成 tinc.conf（服务端不主动连接任何人，包含自定义 Port）
            String interfaceName = TincConfigUtils.resolveInterfaceName(netName);
            TincConfigUtils.createTincConf(gatewayIp, netName, "server_master", "", network.getPort(), interfaceName);

            // D. 生成启停脚本（绑定 .1 网关 IP）
            TincConfigUtils.createTincUpAndDown(gatewayIp, netName, network.getSegment() + ".1");

            // E. 生成 Host 文件（含公网 Address + Port + Subnet + 公钥）
            String subnet = network.getSegment() + ".1/32";
            TincConfigUtils.createHostFile(gatewayIp, netName, "server_master",
                    subnet, gatewayIp, network.getPort(), keyMap.get("publicKey"));

            // F. 生成私钥
            TincConfigUtils.createPrivateKey(gatewayIp, netName, keyMap.get("privateKey"));

            // G. 配置完成后由运行管理器处理防火墙、systemd、接口和双协议监听。
            // 只有数据面全部就绪才提交 READY 状态。
            tincRuntimeManager.ensureNetworkReady(netName);
            network.setNetworkStatus("READY");
            tincNetworkMangeMapper.updateTincNetworkMange(network);

        } catch (Exception e) {
            // 手动回滚：文件生成或推送失败时，回滚 DB 记录
            throw new RuntimeException("内网初始化失败: " + e.getMessage());
        }

        return rows;
    }

    /**
     * 修改 Tinc 网络（同步更新本地副本 + 远程网关）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTincNetworkMange(TincNetworkMange tincNetworkMange)
    {
        // 1. 查出修改前的老数据
        TincNetworkMange oldNetwork = tincNetworkMangeMapper.selectTincNetworkMangeById(tincNetworkMange.getId());
        if (oldNetwork == null) {
            throw new RuntimeException("修改的网络不存在！");
        }

        // 2. 执行数据库更新
        int rows = tincNetworkMangeMapper.updateTincNetworkMange(tincNetworkMange);

        // 3. 同步更新本地 + 远程 Tinc 配置文件
        try {
            String netName = oldNetwork.getNetworkName();

            // A. 读取本地已有的 server_master 主机文件，提取原公钥
            String oldHostContent = TincConfigUtils.readHostFile(netName, "server_master");
            if (oldHostContent == null || oldHostContent.isEmpty()) {
                throw new RuntimeException("找不到服务器原配置文件，无法提取原有公钥！");
            }

            // B. 正则精准提取完整公钥 PEM 块
            String publicKey = "";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?s)-----BEGIN.*?-----END[^-]+-----");
            java.util.regex.Matcher matcher = pattern.matcher(oldHostContent);
            if (matcher.find()) {
                publicKey = matcher.group(0);
            } else {
                throw new RuntimeException("原配置文件中没有找到标准的公钥 PEM 块，提取失败！");
            }

            // C. 获取最新的接入服务器公网 IP（网关 IP）
            String currentServerName = tincNetworkMange.getServerName();
            if (currentServerName == null || currentServerName.isEmpty()) {
                currentServerName = oldNetwork.getServerName();
            }

            MangeServer query = new MangeServer();
            query.setServerName(currentServerName);
            List<MangeServer> serverList = mangeServerService.selectMangeServerList(query);
            if (serverList == null || serverList.isEmpty()) {
                throw new RuntimeException("系统里找不到名为 [" + currentServerName + "] 的服务器");
            }
            String gatewayIp = serverList.get(0).getServerIp();

            // D. 计算新的中心服务器 Subnet 和 Port
            String newSegment = tincNetworkMange.getSegment();
            if (newSegment == null || newSegment.isEmpty()) {
                newSegment = oldNetwork.getSegment();
            }
            String newSubnet = newSegment + ".1/32";

            String newPort = tincNetworkMange.getPort();
            if (newPort == null || newPort.isEmpty()) {
                newPort = oldNetwork.getPort();
            }

            // E. 重新生成并推送 tinc.conf（以防端口变更）
            TincConfigUtils.createTincConf(gatewayIp, netName, "server_master", "", newPort,
                    TincConfigUtils.resolveInterfaceName(netName));

            // F. 覆写 hosts/server_master（本地 + 远程，含 Address/Port/Subnet/PublicKey）
            TincConfigUtils.createHostFile(gatewayIp, netName, "server_master",
                    newSubnet, gatewayIp, newPort, publicKey);

            // G. 重写网卡启停脚本（网段可能变了）
            TincConfigUtils.createTincUpAndDown(gatewayIp, netName, newSegment + ".1");

            // G. 推送完成后远程重载 tincd
            tincRuntimeManager.reloadNetwork(netName);
            tincRuntimeManager.ensureNetworkReady(netName);
            tincNetworkMange.setNetworkStatus("READY");
            tincNetworkMangeMapper.updateTincNetworkMange(tincNetworkMange);

        } catch (Exception e) {
            // 事务回滚
            throw new RuntimeException("同步修改Tinc配置文件失败: " + e.getMessage());
        }

        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNetworkMangeByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            validateNetworkRecordDeletion(id);
        }
        return tincNetworkMangeMapper.deleteTincNetworkMangeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTincNetworkMangeById(Long id) {
        validateNetworkRecordDeletion(id);
        return tincNetworkMangeMapper.deleteTincNetworkMangeById(id);
    }

    /**
     * 删除仅针对后台管理记录。运行中的网络或仍有节点的网络必须先完成迁移/停用；
     * 此处绝不停止 tincd，也不删除 /etc/tinc 下的配置和私钥。
     */
    private void validateNetworkRecordDeletion(Long id) {
        TincNetworkMange network = tincNetworkMangeMapper.selectTincNetworkMangeById(id);
        if (network == null) {
            return;
        }

        TincNodeMange nodeQuery = new TincNodeMange();
        nodeQuery.setNetworkName(network.getNetworkName());
        List<TincNodeMange> nodes = tincNodeMangeMapper.selectTincNodeMangeList(nodeQuery);
        if (nodes != null && !nodes.isEmpty()) {
            throw new IllegalStateException("TINC_NETWORK_IN_USE: 网络 [" + network.getNetworkName()
                    + "] 仍有关联节点，请先删除或迁移节点");
        }

        TincNetworkStatus status = tincRuntimeManager.inspectNetworkStatus(network.getNetworkName());
        if (status.isSystemdActive() || status.isMainPidPresent() || status.isInterfacePresent()
                || status.isTcpListening() || status.isUdpListening()) {
            throw new IllegalStateException("TINC_NETWORK_ACTIVE: 网络 [" + network.getNetworkName()
                    + "] 仍在运行，禁止删除管理记录；请先通过独立维护流程停用网络");
        }

        log.warn("删除 Tinc 网络管理记录但保留物理配置: id={}, netName={}", id, network.getNetworkName());
    }

    @Override
    public List<Long> getAvailablePorts(){
        List<Long> ports = new ArrayList<>();
        List<MangeServer> serverList = mangeServerService.selectMangeServerList(new MangeServer());
        for(MangeServer server:serverList){
            Long startPort = server.getStartPort();
            Long endPort = server.getEndPort();
            if(startPort != null && endPort != null){
                for(Long port = startPort; port <= endPort; port++){
                    ports.add(port);
                }
            }
        }
        return ports;
    }

    @Override
    public List<String> getAvailableSegments(){
        List<String> segments = new ArrayList<>();
        List<MangeServer> serverList = mangeServerService.selectMangeServerList(new MangeServer());
        for(MangeServer server:serverList){
            String startSegment = server.getStartSegment();
            String endSegment  = server.getEndSegment();
            if(startSegment != null && endSegment != null){
                segments.add(startSegment);
                segments.add(endSegment);
            }
        }
        return segments;
    }
}

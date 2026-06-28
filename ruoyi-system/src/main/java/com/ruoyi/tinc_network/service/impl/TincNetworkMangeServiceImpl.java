package com.ruoyi.tinc_network.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.RsaUtils;
import com.ruoyi.common.utils.ShellUtils;      // 必须引入我们写的 Shell 工具
import com.ruoyi.common.utils.TincConfigUtils; // 必须引入我们写的 Config 工具
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 引入事务控制
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;

import com.ruoyi.tinc_server.service.*;
import com.ruoyi.tinc_server.domain.*;

/**
 * Tinc内网集群管理Service业务层处理
 * @author sun
 */
@Service
public class TincNetworkMangeServiceImpl implements ITincNetworkMangeService
{
    @Autowired
    private TincNetworkMangeMapper tincNetworkMangeMapper;

    @Autowired
    private IMangeServerService mangeServerService;

    // ... selectTincNetworkMangeById 保持不变 ...
    @Override
    public TincNetworkMange selectTincNetworkMangeById(Long id)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeById(id);
    }

    // ... selectTincNetworkMangeList 保持不变 ...
    @Override
    public List<TincNetworkMange> selectTincNetworkMangeList(TincNetworkMange tincNetworkMange)
    {
        return tincNetworkMangeMapper.selectTincNetworkMangeList(tincNetworkMange);
    }

    /**
     * 新增Tinc内网集群管理 (核心改造)
     * 对应 PHP Netmanagement::add
     */
    // 在 TincNetworkMangeServiceImpl.java 中替换原有的 insert 方法
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTincNetworkMange(TincNetworkMange network)
    {
        // 1. 设置基础信息并入库
        network.setCreateTime(DateUtils.getNowDate());
        network.setNetworkStatus("正常运行中");
        int rows = tincNetworkMangeMapper.insertTincNetworkMange(network);

        // 2. [新增] 立即生成服务端文件到 D 盘
        try {
            String netName = network.getNetworkName();

            // =========================================================
            // 🔍 关键步骤：根据你选的“服务器名字”去查它的“真实IP”
            // =========================================================

            // 1. 获取你在下拉框里选的名字 (比如 "Tinc_Test")
            String selectedServerName = network.getServerName();
            // 注意：请确认你的实体类里字段名是 getAccessServer() 还是 getAccessServerName()

            if (selectedServerName == null || selectedServerName.isEmpty()) {
                throw new RuntimeException("请选择接入服务器！");
            }

            // 2. 去服务器表里查这个名字对应的记录
            MangeServer query = new MangeServer();
            query.setServerName(selectedServerName); // 假设 MangeServer 实体类用 setServerName 查询
            List<MangeServer> serverList = mangeServerService.selectMangeServerList(query);

            if (serverList == null || serverList.isEmpty()) {
                throw new RuntimeException("系统里找不到名为 [" + selectedServerName + "] 的服务器，请检查服务器集群管理！");
            }

            // 3. 拿到真实的公网 IP
            String realPublicIp = serverList.get(0).getServerIp();
            // =========================================================

            // A. 初始化目录
            TincConfigUtils.initNetworkEnv(netName);

            // B. 生成 4096位 密钥
            Map<String, String> keyMap = RsaUtils.generateKeys();

            // C. 生成配置 (server_master)
            TincConfigUtils.createTincConf(netName, "server_master", "");

            // 【新增这一行】：自动生成并提权 tinc-up 和 tinc-down
            // 这里的 IP 必须是 .1，比如 10.1.11.1
            TincConfigUtils.createTincUpAndDown(netName, network.getSegment() + ".1");

            // D. 生成 Host 文件 (使用刚才查出来的 realPublicIp)
            // 这里的 subnet 是你填的网段 (比如 192.168.103) + .1/32
            String subnet = network.getSegment() + ".1/32";

            // 👇 这里填入真实的 IP
            TincConfigUtils.createHostFile(netName, "server_master", subnet, realPublicIp, keyMap.get("publicKey"));

            // E. 生成私钥
            TincConfigUtils.createPrivateKey(netName, keyMap.get("privateKey"));

        } catch (Exception e) {
            // 手动回滚：如果文件生成失败，把刚才插入数据库的那条记录也删了，保证数据一致性
            throw new RuntimeException("内网初始化失败: " + e.getMessage());
        }

        return rows;
    }


    // ..., delete 方法保持不变 ...
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTincNetworkMange(TincNetworkMange tincNetworkMange)
    {
        // 1. 查出修改前的老数据（因为我们需要知道网络名称，防止前端没有传过来完整实体）
        TincNetworkMange oldNetwork = tincNetworkMangeMapper.selectTincNetworkMangeById(tincNetworkMange.getId());
        if (oldNetwork == null) {
            throw new RuntimeException("修改的网络不存在！");
        }

        // 2. 执行数据库更新
        int rows = tincNetworkMangeMapper.updateTincNetworkMange(tincNetworkMange);

        // 3. 同步更新本地/云端的 Tinc 配置文件
        try {
            String netName = oldNetwork.getNetworkName();

            // A. 读取该网络下原本已经存在的 server_master 主机文件内容
            String oldHostContent = TincConfigUtils.readHostFile(netName, "server_master");
            if (oldHostContent == null || oldHostContent.isEmpty()) {
                throw new RuntimeException("找不到服务器原配置文件，无法提取原有公钥！");
            }

            // B. 【核心步骤】使用正则精准提取完整的公钥块（连头带尾完整剥离，保留 RSA 标识和换行）
            String publicKey = "";
            // (?s) 代表单行模式，让 . 能够匹配包括换行符在内的所有字符
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?s)-----BEGIN.*?-----END[^-]+-----");
            java.util.regex.Matcher matcher = pattern.matcher(oldHostContent);

            if (matcher.find()) {
                publicKey = matcher.group(0); // 完美拿到自带标准头尾的完整公钥字符串
            } else {
                throw new RuntimeException("原配置文件中没有找到标准的公钥 PEM 块，提取失败！");
            }

            // C. 获取最新的接入服务器公网 IP
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
            String realPublicIp = serverList.get(0).getServerIp();

            // D. 计算出全新的中心服务器 Subnet (拿新修改的网段拼接 .1/32)
            String newSegment = tincNetworkMange.getSegment();
            if (newSegment == null || newSegment.isEmpty()) {
                newSegment = oldNetwork.getSegment(); // 如果前端没传新网段，维持原状
            }
            String newSubnet = newSegment + ".1/32";

            // E. 重新调用你写好的 createHostFile 工具类，用“新Subnet+新IP+老公钥”直接覆盖老文件
            TincConfigUtils.createHostFile(netName, "server_master", newSubnet, realPublicIp, publicKey);

            // F. 【重要补充！】既然网段变了，必须强行重写服务器的网卡启停脚本，让它绑定新的 .1 IP！
            TincConfigUtils.createTincUpAndDown(netName, newSegment + ".1");

        } catch (Exception e) {
            // 事务控制：文件要是改乱了或者抛异常了，直接回滚数据库
            throw new RuntimeException("同步修改Tinc配置文件失败: " + e.getMessage());
        }

        return rows;
    }


    @Override
    public int deleteTincNetworkMangeByIds(Long[] ids) {
        return tincNetworkMangeMapper.deleteTincNetworkMangeByIds(ids);
    }

    @Override
    public int deleteTincNetworkMangeById(Long id) {
        return tincNetworkMangeMapper.deleteTincNetworkMangeById(id);
    }

    // ... getAvailablePorts, getAvailableSegments 保持不变 ...
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
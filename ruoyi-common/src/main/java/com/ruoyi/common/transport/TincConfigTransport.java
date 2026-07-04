package com.ruoyi.common.transport;

import java.util.Map;

/**
 * Tinc 配置文件传输抽象层
 * <p>
 * 将生成的配置文件和密钥推送到实际运行 tincd 的 VPN 网关服务器上，
 * 解决管理后台与 Tinc 网关分离部署 / 集群部署时的配置同步问题。
 * </p>
 *
 * @author Sun
 * @date 2026-07-03
 */
public interface TincConfigTransport {

    /**
     * 推送一个配置文件到目标网关
     *
     * @param targetHost 目标网关 IP 地址
     * @param remotePath 目标网关上的绝对路径（如 /etc/tinc/mynet/tinc.conf）
     * @param content    文件内容（纯文本，UTF-8）
     * @param executable 是否为可执行脚本（tinc-up/down 需要 +x）
     */
    void pushFile(String targetHost, String remotePath, String content, boolean executable);

    /**
     * 推送整个目录到目标网关（如 hosts/ 目录下的所有节点公钥文件）
     *
     * @param targetHost 目标网关 IP 地址
     * @param remoteDir  目标网关上的目录路径
     * @param files      文件名（相对路径）→ 文件内容的映射
     */
    void pushDirectory(String targetHost, String remoteDir, Map<String, String> files);

    /**
     * 重载目标网关上指定网络的 tincd 配置
     * <p>等效于: {@code tincd -n <netName> -kHUP}</p>
     *
     * @param targetHost 目标网关 IP 地址
     * @param netName    Tinc 网络名称
     */
    void reloadTinc(String targetHost, String netName);

    /**
     * 重启目标网关上指定网络的 tincd 服务（systemd 方式）
     * <p>等效于: {@code systemctl restart tinc@<netName>}</p>
     *
     * @param targetHost 目标网关 IP 地址
     * @param netName    Tinc 网络名称
     */
    void restartTinc(String targetHost, String netName);

    /**
     * 检查目标机器是否已安装 tinc
     *
     * @param targetHost 目标网关 IP 地址
     * @return true 表示 tincd 命令可用
     */
    boolean isTincInstalled(String targetHost);

    /**
     * 在目标机器上自动安装 tinc（自动检测包管理器 apt / yum / dnf）
     * <p>安装完成后还会创建 /etc/tinc 目录结构</p>
     *
     * @param targetHost 目标网关 IP 地址
     * @return 安装结果描述（如 "apt-get install tinc -y" 的输出摘要）
     */
    String installTinc(String targetHost);

    /**
     * 物理删除网关上的指定网络目录并停止 tincd 守护进程
     *
     * @param targetHost 目标网关 IP 地址
     * @param netName    Tinc 网络名称
     */
    void deleteNetwork(String targetHost, String netName);

    /**
     * 物理删除网关上的指定节点配置文件
     *
     * @param targetHost 目标网关 IP 地址
     * @param netName    Tinc 网络名称
     * @param nodeName   节点名称
     */
    void deleteNode(String targetHost, String netName, String nodeName);
}

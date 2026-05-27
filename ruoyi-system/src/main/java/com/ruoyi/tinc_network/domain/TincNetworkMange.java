package com.ruoyi.tinc_network.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Tinc内网集群管理对象 tinc_network_mange
 * 
 * @author sun
 * @date 2025-12-20
 */
public class TincNetworkMange extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ids */
    @Excel(name = "ids")
    private Long id;

    /** 用户 */
    @Excel(name = "用户")
    private String rootName;

    /** 接入服务器 */
    @Excel(name = "接入服务器")
    private String serverName;

    /** 内网名称 */
    @Excel(name = "内网名称")
    private String networkName;

    /** 端口 */
    private String port;

    /** 网段 */
    @Excel(name = "网段")
    private String segment;

    /** 节点数量 */
    @Excel(name = "节点数量")
    private Long nodes;

    /** 内网状态 */
    @Excel(name = "内网状态")
    private String networkStatus;

    /** 备注 */
    private String explanation;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setRootName(String rootName) 
    {
        this.rootName = rootName;
    }

    public String getRootName() 
    {
        return rootName;
    }

    public void setServerName(String serverName) 
    {
        this.serverName = serverName;
    }

    public String getServerName() 
    {
        return serverName;
    }

    public void setNetworkName(String networkName) 
    {
        this.networkName = networkName;
    }

    public String getNetworkName() 
    {
        return networkName;
    }

    public void setPort(String port) 
    {
        this.port = port;
    }

    public String getPort() 
    {
        return port;
    }

    public void setSegment(String segment) 
    {
        this.segment = segment;
    }

    public String getSegment() 
    {
        return segment;
    }

    public void setNodes(Long nodes) 
    {
        this.nodes = nodes;
    }

    public Long getNodes() 
    {
        return nodes;
    }

    public void setNetworkStatus(String networkStatus) 
    {
        this.networkStatus = networkStatus;
    }

    public String getNetworkStatus() 
    {
        return networkStatus;
    }

    public void setExplanation(String explanation) 
    {
        this.explanation = explanation;
    }

    public String getExplanation() 
    {
        return explanation;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("rootName", getRootName())
            .append("serverName", getServerName())
            .append("networkName", getNetworkName())
            .append("createTime", getCreateTime())
            .append("port", getPort())
            .append("segment", getSegment())
            .append("nodes", getNodes())
            .append("networkStatus", getNetworkStatus())
            .append("explanation", getExplanation())
            .toString();
    }
}

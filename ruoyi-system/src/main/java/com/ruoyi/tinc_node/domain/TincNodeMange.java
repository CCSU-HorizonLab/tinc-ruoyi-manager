package com.ruoyi.tinc_node.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Tinc节点集群管理对象 tinc_node_mange
 * 
 * @author sun
 * @date 2025-12-22
 */
public class TincNodeMange extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ids */
    @Excel(name = "ids")
    private Long id;

    /** 用户 */
    @Excel(name = "用户")
    private String userName;

    /** 设备ID */
    @Excel(name = "设备ID")
    private String tableId;

    /** 接入服务器 */
    @Excel(name = "接入服务器")
    private String serverName;

    /** 所属内网 */
    @Excel(name = "所属内网")
    private String networkName;

    /** 密码 */
    private String password;

    /** 节点名称 */
    @Excel(name = "节点名称")
    private String nodeName;

    /** 内网ip */
    @Excel(name = "内网ip")
    private String networkIp;

    /** 备注 */
    private String explanation;

    /** 节点状态 */
    @Excel(name = "节点状态")
    private String nodeStatus;

    /** 配置状态 */
    @Excel(name = "配置状态")
    private String status;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setTableId(String tableId) 
    {
        this.tableId = tableId;
    }

    public String getTableId() 
    {
        return tableId;
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

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPassword()
    {
        return password;
    }

    public void setNodeName(String nodeName) 
    {
        this.nodeName = nodeName;
    }

    public String getNodeName() 
    {
        return nodeName;
    }

    public void setNetworkIp(String networkIp)
    {
        this.networkIp = networkIp;
    }

    public String getNetworkIp()
    {
        return networkIp;
    }

    public void setExplanation(String explanation)
    {
        this.explanation = explanation;
    }

    public String getExplanation()
    {
        return explanation;
    }

    public void setNodeStatus(String nodeStatus) 
    {
        this.nodeStatus = nodeStatus;
    }

    public String getNodeStatus() 
    {
        return nodeStatus;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userName", getUserName())
            .append("tableId", getTableId())
            .append("serverName", getServerName())
            .append("networkName", getNetworkName())
            .append("password", getPassword())
            .append("nodeName", getNodeName())
            .append("networkIp", getNetworkIp())
            .append("explanation", getExplanation())
            .append("createTime", getCreateTime())
            .append("nodeStatus", getNodeStatus())
            .append("status", getStatus())
            .toString();
    }
}

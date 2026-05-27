package com.ruoyi.tinc_server.domain;

import javax.validation.constraints.Pattern;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 服务器集群管理对象 mange_server
 * 
 * @author sun
 * @date 2025-12-18
 */
public class MangeServer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    @Excel(name = "id")
    private Long id;

    /** 服务器名字 */
    @Excel(name = "服务器名字")
    private String serverName;

    /** 服务器ip */
    @Excel(name = "服务器ip")
    @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d\\d?)$", message = "服务器ip格式不正确，应为xxx.xxx.xxx.xxx")
    private String serverIp;
    
    /** 起始网段 */
    @Excel(name = "起始网段")
    @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){0,2}(25[0-5]|2[0-4]\\d|[0-1]?\\d\\d?)$", message = "起始网段格式不正确，应为xxx.xxx.xxx或xxx.xxx")
    private String startInterat;

    /** 终止网段 */
    @Excel(name = "终止网段")
    @Pattern(regexp= "^((25[0-5]|2[0-4]\\d|[0-1]?\\d\\d?)\\.){0,2}(25[0-5]|2[0-4]\\d|[0-1]?\\d\\d?)$", message = "终止网段格式不正确，应为xxx.xxx.xxx或xxx.xxx")
    private String endInterat;

    /** 起始端口 */
    private Long startPort;

    /** 终止端口 */
    private Long endPort;

    /** 备注 */
    private String remark;

    /** 内网数量 */
    @Excel(name = "内网数量")
    private Long number;

    /** 状态 */
    @Excel(name = "状态")
    private Long status;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setServerName(String serverName) 
    {
        this.serverName = serverName;
    }

    public String getServerName() 
    {
        return serverName;
    }

    public void setServerIp(String serverIp) 
    {
        this.serverIp = serverIp;
    }

    public String getServerIp() 
    {
        return serverIp;
    }

    public void setStartInterat(String startInterat) 
    {
        this.startInterat = startInterat;
    }

    public String getStartInterat() 
    {
        return startInterat;
    }

    public void setEndInterat(String endInterat) 
    {
        this.endInterat = endInterat;
    }

    public String getEndInterat() 
    {
        return endInterat;
    }

    public void setStartPort(Long startPort)
    {
        this.startPort = startPort;
    }

    public Long getStartPort()
    {
        return startPort;
    }

    public void setEndPort(Long endPort)
    {
        this.endPort = endPort;
    }

    public Long getEndPort()
    {
        return endPort;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setNumber(Long number) 
    {
        this.number = number;
    }

    public Long getNumber() 
    {
        return number;
    }

    public void setStatus(Long status)
    {
        this.status = status;
    }

    public Long getStatus()
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("serverName", getServerName())
            .append("serverIp", getServerIp())
            .append("startInterat", getStartInterat())
            .append("endInterat", getEndInterat())
            .append("startPort", getStartPort())
            .append("endPort", getEndPort())
            .append("remark", getRemark())
            .append("number", getNumber())
            .append("status", getStatus())
            .toString();
    }
}

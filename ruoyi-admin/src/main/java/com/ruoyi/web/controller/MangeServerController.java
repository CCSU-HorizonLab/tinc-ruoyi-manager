package com.ruoyi.web.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 服务器集群管理Controller
 * 
 * @author sun
 * @date 2025-12-18
 */
@RestController
@RequestMapping({"/tinc/server", "/manger/mangeServer"})
public class MangeServerController extends BaseController
{
    @Autowired
    private IMangeServerService mangeServerService;

    /**
     * 查询服务器集群管理列表
     * 为前端Tinc网络管理的服务器选项框提供数据来源
     * 前端通过调用此接口获取所有可用服务器列表，用于生成服务器选择下拉框
     * 
     * @param mangeServer 服务器集群管理查询条件
     * @return 服务器集群管理列表数据
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:list')")
    @GetMapping("/list")
    public TableDataInfo list(MangeServer mangeServer)
    {
        startPage();
        List<MangeServer> list = mangeServerService.selectMangeServerList(mangeServer);
        return getDataTable(list);
    }

    /**
     * 导出服务器集群管理列表
     * 将服务器集群管理数据导出为Excel文件
     * 
     * @param response HTTP响应对象
     * @param mangeServer 服务器集群管理查询条件
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:export')")
    @Log(title = "服务器集群管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, MangeServer mangeServer)
    {
        List<MangeServer> list = mangeServerService.selectMangeServerList(mangeServer);
        ExcelUtil<MangeServer> util = new ExcelUtil<MangeServer>(MangeServer.class);
        util.exportExcel(response, list, "服务器集群管理数据");
    }

    /**
     * 获取服务器集群管理详细信息
     * 用于编辑服务器时加载数据，以及查看服务器详情
     * 
     * @param Id 服务器集群管理ID
     * @return 服务器集群管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:query')")
    @GetMapping(value = "/{Id}")
    public AjaxResult getInfo(@PathVariable("Id") Long Id)
    {
        return success(mangeServerService.selectMangeServerById(Id));
    }

    /**
     * 新增服务器集群管理
     * 添加新的服务器信息到集群管理中
     * 服务器信息包含IP地址、端口范围、网段范围等，用于Tinc网络的服务器选项框数据
     * 
     * @param mangeServer 服务器集群管理信息
     * @return 新增结果
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:add')")
    @Log(title = "服务器集群管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody @Valid MangeServer mangeServer)
    {
        mangeServer.setCreateBy(getUsername());
        try{
            return toAjax(mangeServerService.insertMangeServer(mangeServer));
        }catch(RuntimeException e){
             return error(e.getMessage());
        }
    }

    /**
     * 修改服务器集群管理
     * 更新服务器集群管理信息
     * 包括服务器的IP地址、端口范围、网段范围等配置
     * 
     * @param mangeServer 服务器集群管理信息
     * @return 修改结果
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:edit')")
    @Log(title = "服务器集群管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody @Valid MangeServer mangeServer)
    {
        mangeServer.setUpdateBy(getUsername());
        try{
            return toAjax(mangeServerService.updateMangeServer(mangeServer));
        }catch(RuntimeException e){
            return error(e.getMessage());
        }
    }

    /**
     * 删除服务器集群管理
     * 批量删除服务器集群管理信息
     *
     *
     * @param Ids 服务器集群管理ID数组
     * @return 删除结果
     */
    @PreAuthorize("@ss.hasPermi('manger:manger:remove')")
    @Log(title = "服务器集群管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{Ids}")
    public AjaxResult remove(@PathVariable Long[] Ids)
    {
        return toAjax(mangeServerService.deleteMangeServerByIds(Ids));
    }
}

package com.ruoyi.web.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
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
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * Tinc内网集群管理Controller
 * 
 * @author sun
 * @date 2025-12-20
 */
@RestController
@RequestMapping({"/tinc/network", "/TincNetworkMange/TincNetworkMange"})
public class TincNetworkMangeController extends BaseController
{
    @Autowired
    private ITincNetworkMangeService tincNetworkMangeService;

    /**
     * 查询Tinc内网集群管理列表
     * 前端表格数据来源，用于展示已创建的Tinc网络集群信息
     * @param tincNetworkMange Tinc内网集群管理查询条件
     * @return 表格数据信息
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:list')")
    @GetMapping("/list")
    public TableDataInfo list(TincNetworkMange tincNetworkMange)
    {
        startPage();
        List<TincNetworkMange> list = tincNetworkMangeService.selectTincNetworkMangeList(tincNetworkMange);
        return getDataTable(list);
    }

    /**
     * 导出Tinc内网集群管理列表
     * 将Tinc网络集群信息导出为Excel文件
     * @param response HTTP响应对象
     * @param tincNetworkMange Tinc内网集群管理查询条件
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:export')")
    @Log(title = "Tinc内网集群管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TincNetworkMange tincNetworkMange)
    {
        List<TincNetworkMange> list = tincNetworkMangeService.selectTincNetworkMangeList(tincNetworkMange);
        ExcelUtil<TincNetworkMange> util = new ExcelUtil<TincNetworkMange>(TincNetworkMange.class);
        util.exportExcel(response, list, "Tinc内网集群管理数据");
    }

    /**
     * 获取Tinc内网集群管理详细信息
     * 用于编辑时加载数据，以及查看详情
     * @param id Tinc内网集群管理ID
     * @return Tinc内网集群管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(tincNetworkMangeService.selectTincNetworkMangeById(id));
    }

    /**
     * 新增Tinc内网集群管理
     * rootName字段由前端自动填充当前登录用户，无需手动输入
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:add')")
    @Log(title = "Tinc内网集群管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TincNetworkMange tincNetworkMange)
    {
        tincNetworkMange.setRootName(getUsername());
        return toAjax(tincNetworkMangeService.insertTincNetworkMange(tincNetworkMange));
    }

    /**
     * 修改Tinc内网集群管理
     * 更新已创建的Tinc网络集群信息
     * @param tincNetworkMange Tinc内网集群管理信息
     * @return 修改结果
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:edit')")
    @Log(title = "Tinc内网集群管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TincNetworkMange tincNetworkMange)
    {
        tincNetworkMange.setUpdateBy(getUsername());
        return toAjax(tincNetworkMangeService.updateTincNetworkMange(tincNetworkMange));
    }

    /**
     * 删除Tinc内网集群管理
     * 批量删除Tinc网络集群信息
     * @param ids Tinc内网集群管理ID数组
     * @return 删除结果
     */
    @PreAuthorize("@ss.hasPermi('TincNetworkMange:TincNetworkMange:remove')")
    @Log(title = "Tinc内网集群管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(tincNetworkMangeService.deleteTincNetworkMangeByIds(ids));
    }
}

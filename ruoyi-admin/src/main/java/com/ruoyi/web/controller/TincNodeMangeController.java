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
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import java.io.File;
import java.io.IOException;

/**
 * Tinc节点集群管理Controller
 * 
 * @author sun
 * @date 2025-12-22
 */
@RestController
@RequestMapping({"/tinc/node", "/node_mange/node_mange"})
public class TincNodeMangeController extends BaseController
{
    @Autowired
    private ITincNodeMangeService tincNodeMangeService;

    /**
     * 查询Tinc节点集群管理列表
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:list')")
    @GetMapping("/list")
    public TableDataInfo list(TincNodeMange tincNodeMange)
    {
        startPage();
        List<TincNodeMange> list = tincNodeMangeService.selectTincNodeMangeList(tincNodeMange);
        return getDataTable(list);
    }

    /**
     * 导出Tinc节点集群管理列表
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:export')")
    @Log(title = "Tinc节点集群管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TincNodeMange tincNodeMange)
    {
        List<TincNodeMange> list = tincNodeMangeService.selectTincNodeMangeList(tincNodeMange);
        ExcelUtil<TincNodeMange> util = new ExcelUtil<TincNodeMange>(TincNodeMange.class);
        util.exportExcel(response, list, "Tinc节点集群管理数据");
    }

    /**
     * 获取Tinc节点集群管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(tincNodeMangeService.selectTincNodeMangeById(id));
    }

    /**
     * 新增Tinc节点集群管理
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:add')")
    @Log(title = "Tinc节点集群管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TincNodeMange tincNodeMange)
    {
        tincNodeMange.setCreateBy(getUsername());
        return toAjax(tincNodeMangeService.insertTincNodeMange(tincNodeMange));
    }

    /**
     * 修改Tinc节点集群管理
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:edit')")
    @Log(title = "Tinc节点集群管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TincNodeMange tincNodeMange)
    {
        tincNodeMange.setUpdateBy(getUsername());
        return toAjax(tincNodeMangeService.updateTincNodeMange(tincNodeMange));
    }

    /**
     * 删除Tinc节点集群管理
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:remove')")
    @Log(title = "Tinc节点集群管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(tincNodeMangeService.deleteTincNodeMangeByIds(ids));
    }

    /**
     * 下载客户端安装包
     */
    @PreAuthorize("@ss.hasPermi('node_mange:node_mange:export')") // 权限字符先借用 export 的
    @PostMapping("/download/{id}")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException
    {
        // 1. 查数据库，获取节点信息
        TincNodeMange node = tincNodeMangeService.selectTincNodeMangeById(id);
        if (node == null) {
            throw new RuntimeException("节点不存在");
        }

        // 2. 拼接文件名 (必须和 Service 里的打包逻辑一致！)
        // 格式：netName_nodeName.zip
        String fileName = node.getNetworkName() + "_" + node.getNodeName() + ".zip";

        // 3. 拼接绝对路径
        // D:/tinc/zips/segment_sun666.zip
        String filePath = TincConfigUtils.getBasePath() + "/zips/" + fileName;

        // 4. 检查文件是否存在
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("安装包文件不存在，请尝试删除节点后重新创建");
        }

        // 5. 设置响应头 (告诉浏览器这是一个要下载的文件)
        response.setContentType("application/octet-stream");
        FileUtils.setAttachmentResponseHeader(response, fileName);

        // 6. 开始写出数据流
        FileUtils.writeBytes(filePath, response.getOutputStream());
    }
}

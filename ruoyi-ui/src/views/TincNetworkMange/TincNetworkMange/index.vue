<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="服务器" prop="serverName">
        <el-input
          v-model="queryParams.serverName"
          placeholder="请输入服务器"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内网名称" prop="networkName">
        <el-input
          v-model="queryParams.networkName"
          placeholder="请输入内网名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="节点数量" prop="nodes">
        <el-input
          v-model="queryParams.nodes"
          placeholder="请输入节点数量"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="TincNetworkMangeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ids" align="center" prop="id" />
      <el-table-column label="用户" align="center" prop="rootName" />
      <el-table-column label="接入服务器" align="center" prop="serverName" />
      <el-table-column label="内网名称" align="center" prop="networkName" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="网段" align="center" prop="segment" />
      <el-table-column label="节点数量" align="center" prop="nodes" />
      <el-table-column label="内网状态" align="center" prop="networkStatus" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['TincNetworkMange:TincNetworkMange:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['TincNetworkMange:TincNetworkMange:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改Tinc内网集群管理对话框 -->
  <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
  <el-form ref="form" :model="form" :rules="rules" label-width="80px">
    <el-form-item label="接入服务器" prop="serverName">
       <el-select v-model="form.serverName" placeholder="请选择接入服务器" @change="onServerChange">
        <el-option v-for="server in serverOptions" :key="server.serverName" :label="server.serverName" :value="server.serverName"></el-option>
      </el-select>
    </el-form-item>
    <el-form-item label="内网名称" prop="networkName">
      <el-input v-model="form.networkName" placeholder="请输入内网名称" />
    </el-form-item>
   <el-form-item label="网段" prop="segment">
       <el-select v-model="form.segment" placeholder="请选择网段" :disabled="!currentServer">
        <el-option v-for="segment in segmentOptions" :key="segment.value" :label="segment.label" :value="segment.value"></el-option>
      </el-select>
   </el-form-item>
    <el-form-item label="端口" prop="port">
       <el-select v-model="form.port" placeholder="请选择端口" :disabled="!currentServer">
        <el-option v-for="port in portOptions" :key="port.value" :label="port.label" :value="port.value"></el-option>
      </el-select>
    </el-form-item>
    <el-form-item label="备注" prop="explanation">
      <el-input v-model="form.explanation" placeholder="请输入备注" />
    </el-form-item>
  </el-form>
  <div slot="footer" class="dialog-footer">
    <el-button type="primary" @click="submitForm" :loading="isSubmitting">确 定</el-button>
    <el-button @click="cancel">取 消</el-button>
  </div>
</el-dialog>
  </div>
</template>

<script>
import { listTincNetworkMange, getTincNetworkMange, delTincNetworkMange, addTincNetworkMange, updateTincNetworkMange } from "@/api/TincNetworkMange/TincNetworkMange"
import { listManger } from "@/api/manger/manger"

export default {
  name: "TincNetworkMange",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // Tinc内网集群管理表格数据
      TincNetworkMangeList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        rootName: null,
        serverName: null,
        networkName: null,
        segment: null,
        nodes: null,
        networkStatus: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        serverName: [
          { required: true, message: "接入服务器不能为空", trigger: "blur" }
        ],
        networkName: [
          { required: true, message: "内网名称不能为空", trigger: "blur" }
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" }
        ],
        port: [
          { required: true, message: "端口不能为空", trigger: "change" },
          { validator: this.validatePort, trigger: "change" }
        ],
        segment: [
          { required: true, message: "网段不能为空", trigger: "change" },
          { validator: this.validateSegment, trigger: "change" }
        ],
        nodes: [
          { required: true, message: "节点数量不能为空", trigger: "blur" }
        ],
        networkStatus: [
          { required: true, message: "内网状态不能为空", trigger: "change" }
        ],
      },
      // 是否正在提交
      isSubmitting: false,
      //接入服务器
      serverOptions: [],
      //当前服务器端口和网段范围
      currentServer: null,
      //网段
      segmentOptions: [],
      //端口
      portOptions: []
    }
  },
  created() {
    this.getList()
    this.getServerOptions();
  },
  activated() {
    this.getServerOptions(); // 只要切回页面，就刷新服务器列表
  },
  methods: {
    /** 查询Tinc内网集群管理列表 */
    getList() {
      this.loading = true
      listTincNetworkMange(this.queryParams).then(response => {
        this.TincNetworkMangeList = response.rows
        this.total = response.total
        // 调试：查看返回的数据结构
        console.log('返回的数据列表:', response.rows);
        if (response.rows.length > 0) {
          console.log('第一条数据的结构:', response.rows[0]);
          console.log('第一条数据的rootName:', response.rows[0].rootName);
        }
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        rootName: null,
        serverName: null,
        networkName: null,
        createTime: null,
        port: null,
        segment: null,
        nodes: null,
        networkStatus: '在线',
        explanation: null
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      // 修复：将getter名称从username改为name，确保正确获取当前登录用户
      this.form.rootName = this.$store.getters.name;
      console.log('当前登录用户:', this.$store.getters.name);
      console.log('表单中的rootName:', this.form.rootName);
      // Reset server related state for new entry
      this.currentServer = null;
      this.segmentOptions = [];
      this.portOptions = [];
      this.form.serverName = '';
      this.open = true
      this.title = "添加Tinc内网集群管理"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getTincNetworkMange(id).then(response => {
        this.form = response.data
        // 保存当前端口和网段值
        const savedPort = this.form.port;
        const savedSegment = this.form.segment;
        // 触发服务器变更事件，加载对应的端口和网段选项
        this.onServerChange(this.form.serverName);
        // 恢复保存的端口和网段值
        this.form.port = savedPort;
        this.form.segment = savedSegment;
        this.open = true
        this.title = "修改Tinc内网集群管理"
      })
    },
    /** 提交按钮 */
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      // 确保rootName始终为当前登录用户
      this.form.rootName = this.$store.getters.name;
      console.log('当前登录用户:', this.$store.getters.name);
      console.log('提交时的rootName:', this.form.rootName);
      
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateTincNetworkMange(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).catch(error => {
              this.$modal.msgError(`修改失败: ${error.response?.data?.msg || error.message}`)
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addTincNetworkMange(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            }).catch(error => {
              this.$modal.msgError(`新增失败: ${error.response?.data?.msg || error.message}`)
            }).finally(() => {
              this.isSubmitting = false;
            })
          }
        } else {
          this.isSubmitting = false;
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除Tinc内网集群管理编号为"' + ids + '"的数据项？').then(function() {
        return delTincNetworkMange(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('TincNetworkMange/TincNetworkMange/export', {
        ...this.queryParams
      }, `TincNetworkMange_${new Date().getTime()}.xlsx`)
    },
    
    /**
     * 获取服务器列表
     * 为前端接入服务器选项框提供数据来源
     * 从服务器集群管理API获取所有可用服务器
     */
    getServerOptions(){
      listManger({}).then(response => {
        this.serverOptions = response.rows;
      }).catch(error => {
        console.error('获取服务器列表失败:', error);
        this.$modal.msgError('获取服务器列表失败');
      });
    },
    
    /**
     * 服务器选择变化事件处理
     * 当用户选择不同的服务器时，动态生成该服务器可用的端口和网段选项
     * 
     * @param {string} serverName - 选中的服务器名称
     */
    onServerChange(serverName){
      // 根据服务器名称查找对应的服务器信息
      const server = this.serverOptions.find(s => s.serverName === serverName); 
      this.currentServer = server;
      
      // 重置表单中的端口和网段，因为服务器变化后可用范围可能不同
      this.form.port = '';
      this.form.segment = '';
      
      if(server){
        try {
          // 根据服务器的起始和结束网段生成网段选项
          this.segmentOptions = this.generateSegmentOptions(server.startInterat, server.endInterat);
          // 根据服务器的起始和结束端口生成端口选项
          this.portOptions = this.generatePortOptions(server.startPost, server.endPost);
        } catch (error) {
          console.error('生成选项失败:', error);
          this.segmentOptions = [];
          this.portOptions = [];
        }
      }else{
        // 如果没有选中服务器，清空端口和网段选项
        this.segmentOptions = [];
        this.portOptions = [];
      }
    },
    
    /**
     * 生成网段选项列表
     * 根据服务器的起始和结束网段生成完整的网段选项
     * 假设网段格式为"192.168.x"，生成从startInterat到endInterat的所有网段
     * 
     * @param {string} startInterat - 起始网段，如"192.168.1"
     * @param {string} endInterat - 结束网段，如"192.168.3"
     * @return {Array} 网段选项列表，每个选项包含label和value
     */
    generateSegmentOptions(startInterat, endInterat) {
      const segments = [];
      if (!startInterat || !endInterat) return segments;
      
      try {
        // 将网段字符串分割为数组，如"192.168.1" → ["192", "168", "1"]
        const startParts = startInterat.split('.');
        const endParts = endInterat.split('.');
        
        // 简化处理，只处理相同前缀的情况（如192.168.x）
        if (startParts.length === 3 && endParts.length === 3 && 
            startParts[0] === endParts[0] && startParts[1] === endParts[1]) {
          
          const start = parseInt(startParts[2]);
          const end = parseInt(endParts[2]);
          
          if (!isNaN(start) && !isNaN(end) && start <= end) {
            // 生成从起始网段到结束网段的所有网段选项
            for (let i = start; i <= end; i++) {
              segments.push({
                label: `${startParts[0]}.${startParts[1]}.${i}`,
                value: `${startParts[0]}.${startParts[1]}.${i}`
              });
            }
          }
        }
      } catch (error) {
        console.error('生成网段选项失败:', error);
      }
      
      return segments;
    },
    
    /**
     * 生成端口选项列表
     * 根据服务器的起始和结束端口生成完整的端口选项
     * 限制最多生成100个端口，避免选项过多影响性能
     * 
     * @param {string|number} startPost - 起始端口
     * @param {string|number} endPost - 结束端口
     * @return {Array} 端口选项列表，每个选项包含label和value
     */
    generatePortOptions(startPost, endPost) {
      const ports = [];
      if (!startPost || !endPost) return ports;
      
      try {
        const start = parseInt(startPost);
        const end = parseInt(endPost);
        
        if (!isNaN(start) && !isNaN(end) && start <= end) {
          // 限制生成的端口数量，避免选项过多导致页面卡顿
          const maxPorts = 100;
          const actualEnd = Math.min(end, start + maxPorts);
          
          // 生成从起始端口到结束端口的所有端口选项
          for (let i = start; i <= actualEnd; i++) {
            ports.push({
              label: `${i}`,
              value: `${i}`
            });
          }
        }
      } catch (error) {
        console.error('生成端口选项失败:', error);
      }
      
      return ports;
    },
    
    // 验证端口是否在服务器范围内
    validatePort(rule, value, callback) {
      if (!value) {
        return callback(new Error('端口不能为空'));
      }
      
      if (this.currentServer) {
        const port = parseInt(value);
        const startPort = parseInt(this.currentServer.startPost);
        const endPort = parseInt(this.currentServer.endPost);
        
        if (isNaN(port) || port < startPort || port > endPort) {
          return callback(new Error(`端口必须在 ${startPort} 到 ${endPort} 之间`));
        }
      }
      
      callback();
    },
    
    // 验证网段是否在服务器范围内
    validateSegment(rule, value, callback) {
      if (!value) {
        return callback(new Error('网段不能为空'));
      }
      
      if (this.currentServer) {
        const startInterat = this.currentServer.startInterat;
        const endInterat = this.currentServer.endInterat;
        
        // 简单验证：检查网段是否在范围内
        const startParts = startInterat.split('.');
        const endParts = endInterat.split('.');
        const valueParts = value.split('.');
        
        if (startParts.length === 3 && endParts.length === 3 && valueParts.length === 3) {
          if (startParts[0] === endParts[0] && startParts[0] === valueParts[0] &&
              startParts[1] === endParts[1] && startParts[1] === valueParts[1]) {
            
            const start = parseInt(startParts[2]);
            const end = parseInt(endParts[2]);
            const valueNum = parseInt(valueParts[2]);
            
            if (isNaN(valueNum) || valueNum < start || valueNum > end) {
              return callback(new Error(`网段必须在 ${startInterat} 到 ${endInterat} 之间`));
            }
          }
        }
      }
      
      callback();
    }
  }
}
</script>

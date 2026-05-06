<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="用户" prop="useName">
        <el-input
          v-model="queryParams.useName"
          placeholder="请输入用户"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="设备ID" prop="tableId">
        <el-input
          v-model="queryParams.tableId"
          placeholder="请输入设备ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="接入服务器" prop="serverName">
        <el-input
          v-model="queryParams.serverName"
          placeholder="请输入接入服务器"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属内网" prop="networkName">
        <el-input
          v-model="queryParams.networkName"
          placeholder="请输入所属内网"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="节点名称" prop="nodeName">
        <el-input
          v-model="queryParams.nodeName"
          placeholder="请输入节点名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内网ip" prop="networkIp">
        <el-input
          v-model="queryParams.networkIp"
          placeholder="请输入内网ip"
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
          v-hasPermi="['node_mange:node_mange:add']"
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
          v-hasPermi="['node_mange:node_mange:edit']"
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
          v-hasPermi="['node_mange:node_mange:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['node_mange:node_mange:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="node_mangeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ids" align="center" prop="id" />
      <el-table-column label="用户" align="center" prop="useName" />
      <el-table-column label="设备ID" align="center" prop="tableId" />
      <el-table-column label="接入服务器" align="center" prop="serverName" />
      <el-table-column label="所属内网" align="center" prop="networkName" />
      <el-table-column label="节点名称" align="center" prop="nodeName" />
      <el-table-column label="内网ip" align="center" prop="networkIp" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>                 
      </el-table-column>
      <el-table-column label="节点状态" align="center" prop="nodeStatus" />
      <el-table-column label="配置状态" align="center" prop="status" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['node_mange:node_mange:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['node_mange:node_mange:remove']"
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

    <!-- 添加或修改Tinc节点集群管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <!-- 接入服务器选择框组件 -->
        <el-form-item label="接入服务器" prop="serverName">
          <el-select v-model="form.serverName" placeholder="请选择接入服务器" @change="onServerChange">
            <el-option v-for="server in serverOptions" :key="server.serverName" :label="server.serverName" :value="server.serverName"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="所属内网" prop="networkName">
           <el-select v-model="form.networkName" placeholder="请选择所属内网" :disabled="!form.serverName" @change="onNetworkChange">
            <el-option v-for="network in networkOptions" :key="network.networkName" :label="network.networkName" :value="network.networkName"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="passwrod">
          <el-input v-model="form.passwrod" placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="请输入节点名称" />
        </el-form-item>
        <el-form-item label="内网ip" prop="networkIp">
          <el-input v-model="form.networkIp" placeholder="请输入内网ip" />
        </el-form-item>
        <el-form-item label="备注" prop="explantion">
          <el-input v-model="form.explantion" placeholder="请输入备注" />
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
import { listNode_mange, getNode_mange, delNode_mange, addNode_mange, updateNode_mange } from "@/api/node_mange/node_mange"
import { listManger } from "@/api/manger/manger"
import { listTincNetworkMange} from "@/api/TincNetworkMange/TincNetworkMange"

export default {
  name: "Node_mange",
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
      // Tinc节点集群管理表格数据
      node_mangeList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        useName: null,
        tableId: null,
        serverName: null,
        networkName: null,
        nodeName: null,
        networkIp: null,
        nodeStatus: null,
        status: null
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        useName: [
          { required: true, message: "用户不能为空", trigger: "blur" }
        ],
        tableId: [
          { required: true, message: "设备ID不能为空", trigger: "blur" }
        ],
        serverName: [
          { required: true, message: "接入服务器不能为空", trigger: "blur" }
        ],
        networkName: [
          { required: true, message: "所属内网不能为空", trigger: "blur" }
        ],
        passwrod: [
          { required: true, message: "密码不能为空", trigger: "blur" }
        ],
        nodeName: [
          { required: true, message: "节点名称不能为空", trigger: "blur" }
        ],
        networkIp: [
          { required: true, message: "内网ip不能为空", trigger: "blur" }
        ],
        explantion: [
          { required: true, message: "备注不能为空", trigger: "blur" }
        ],
        nodeStatus: [
          { required: true, message: "节点状态不能为空", trigger: "change" }
        ],
        status: [
          { required: true, message: "配置状态不能为空", trigger: "change" }
        ]
      },
      // 是否正在提交
      isSubmitting: false,
      // 接入服务器选项
      serverOptions: [], // 接入服务器选项列表
      // 当前服务器信息
      currentServer: null ,// 当前选中的服务器信息
      // 所属内网选项表
      networkOptions: [] // 所属内网选项列表
    }
  },
  created() {
    this.getList()
    this.getServerOptions() // 初始化时获取服务器列表
  },
  activated() {
    this.getServerOptions(); // 只要页面切回来，就自动刷新服务器列表数据
  },
  methods: {
    /** 查询Tinc节点集群管理列表 */
    getList() {
      this.loading = true
      listNode_mange(this.queryParams).then(response => {
        this.node_mangeList = response.rows
        this.total = response.total
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
        useName: null,
        tableId: null,
        serverName: null,
        networkName: null,
        passwrod: null,
        nodeName: null,
        networkIp: null,
        explantion: null,
        createTime: null,
        nodeStatus: null,
        status: null
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
      // Reset server related state for new entry
      this.currentServer = null
      this.form.serverName = ''
      this.open = true
      this.title = "添加Tinc节点集群管理"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getNode_mange(id).then(response => {
        this.form = response.data
        // 触发服务器变更事件，更新当前服务器信息
        this.onServerChange(this.form.serverName)
        this.open = true
        this.title = "修改Tinc节点集群管理"
      })
    },
    /** 提交按钮 */
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true; // 【终极加固】进门立即反锁，不给任何异步任务留间隙
      
      // 修复：将getter名称从username改为name，确保正确获取当前登录用户
      this.form.useName = this.$store.getters.name;
      this.form.nodeStatus = this.form.nodeStatus || '0';
      this.form.status = this.form.status || '0';
      
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateNode_mange(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addNode_mange(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          }
        } else {
          this.isSubmitting = false; // 校验不通过，解锁让用户修改后再提
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除Tinc节点集群管理编号为"' + ids + '"的数据项？').then(function() {
        return delNode_mange(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('node_mange/node_mange/export', {
        ...this.queryParams
      }, `node_mange_${new Date().getTime()}.xlsx`)
    },
    
    /**
     * 获取服务器列表
     * 为前端接入服务器选项框提供数据来源
     * 从服务器集群管理API获取所有可用服务器
     */
    getServerOptions() {
      // 从服务器集群管理API获取所有可用服务器
      listManger({}).then(response => {
        this.serverOptions = response.rows
      }).catch(error => {
        console.error('获取服务器列表失败:', error)
        this.$modal.msgError('获取服务器列表失败')
      })
    },
    /**
     * 服务器选择变化事件处理
     * 当用户选择不同的服务器时，更新当前服务器信息
     * 
     * @param {string} serverName - 选中的服务器名称
     */
    onServerChange(serverName) {
      // 根据服务器名称查找对应的服务器信息
      const server = this.serverOptions.find(s => s.serverName === serverName)
      this.currentServer = server
      // 清空当前内网选项
      this.form.networkName = ''
      this.networkOptions = []

      //如果选择了服务器，加载其内网列表
      if(server){
        this.loadNetworkOptions(server.serverName)
      }
    },
     /**
     * 加载指定服务器下的内网列表
     * 当用户选择服务器后，根据服务器名称加载对应的内网列表
     * 
     * @param {string} serverName - 服务器名称
     * 
     */
    loadNetworkOptions(serverName){
      //根据服务器名称加载内网列表
      listTincNetworkMange({serverName : serverName}).then(response =>{
        // 从API响应中提取内网列表
        this.networkOptions = response.rows
      }).catch(error =>{
        console.error('获取内网列表失败:', error)
        this.$modal.msgError('获取内网列表失败')
      })
    },
    /**
     * 加载指定服务器下的内网列表
     * 当用户选择服务器后，根据服务器名称加载对应的内网列表
     * 
     * @param {string} network - 内网名称
     * 
     */
    /**
     * 内网选择变化事件
     * 自动根据内网网段填充IP前缀
     */
    // onNetworkChange(networkName){
    //   // 根据服务器名称查找对应的服务器信息
    //   const network = this.networkOptions.find(n => n.networkName === networkName)

    //   if(network && network.segment){
    //     //
    //     const segment = network.segment.split('/')[0]
    //     const ipParts = segment.split('.')
    //     if(ipParts.length === 3){
    //       //
    //       this.form.networkIp = `${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.`
    //     }
    //   }
    // }
    onNetworkChange(networkName) {
      console.log('1. 触发内网选择变更，选中的内网名称:', networkName);
      
      // 1. 查找对应的内网对象
      const network = this.networkOptions.find(n => n.networkName === networkName);
      
      if (!network) {
        console.warn('2. 未找到对应的内网对象，请检查 networkName 是否一致');
        return;
      }
      console.log('2. 找到内网对象:', network);
      console.log('3. 数据库中的 segment 字段值为:', network.segment);

      // 2. 校验 segment 是否存在
      if (network.segment) {
        // 3. 处理网段逻辑 (例如: "192.168.1.0/24")
        try {
          const segmentIP = network.segment.split('/')[0]; // 拿到 "192.168.1.0"
          const ipParts = segmentIP.split('.'); // 拿到 ["192", "168", "1", "0"]
          
          console.log('4. 拆分后的IP段:', ipParts);

          // 只要能拆分出至少3段，我们就尝试填充 (放宽限制，兼容性更好)
          if (ipParts.length >= 3) {
            // 拼接前三段: 192.168.1.
            const autoIpPrefix = `${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.`;
            
            // 赋值给表单
            this.form.networkIp = autoIpPrefix;
            // 强制 Vue 更新视图 (防止有时候赋值了但不显示)
            this.$forceUpdate();
            
            console.log('5. 自动填充成功，设置的值为:', autoIpPrefix);
          } else {
            console.warn('5. segment 格式无法解析为IP，拆分长度不足3');
          }
        } catch (e) {
          console.error('解析 segment 出错:', e);
        }
      } else {
        console.warn('4. 该内网数据的 segment 字段为空，无法自动填充');
        // 如果没有网段，也可以考虑清空或者保留原值，这里暂时不做操作
      }
    },
  }
}
</script>

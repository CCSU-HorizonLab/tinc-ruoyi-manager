<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="用户" prop="userName">
        <el-input
          v-model="queryParams.userName"
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

    <el-table v-loading="loading" :data="nodeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ids" align="center" prop="id" />
      <el-table-column label="用户" align="center" prop="userName" />
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

    <!-- 添加或修改节点管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
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
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="请输入节点名称" />
        </el-form-item>
        <el-form-item label="内网ip" prop="networkIp">
          <el-input v-model="form.networkIp" placeholder="请输入内网ip" />
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
import { listNode, getNode, delNode, addNode, updateNode } from "@/api/tinc/node"
import { listServer } from "@/api/tinc/server"
import { listNetwork } from "@/api/tinc/network"

export default {
  name: "Node",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      nodeList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: null,
        tableId: null,
        serverName: null,
        networkName: null,
        nodeName: null,
        networkIp: null,
        nodeStatus: null,
        status: null
      },
      form: {},
      rules: {
        userName: [
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
        password: [
          { required: true, message: "密码不能为空", trigger: "blur" }
        ],
        nodeName: [
          { required: true, message: "节点名称不能为空", trigger: "blur" }
        ],
        networkIp: [
          { required: true, message: "内网ip不能为空", trigger: "blur" }
        ],
        explanation: [
          { required: true, message: "备注不能为空", trigger: "blur" }
        ],
        nodeStatus: [
          { required: true, message: "节点状态不能为空", trigger: "change" }
        ],
        status: [
          { required: true, message: "配置状态不能为空", trigger: "change" }
        ]
      },
      isSubmitting: false,
      serverOptions: [],
      currentServer: null,
      networkOptions: []
    }
  },
  created() {
    this.getList()
    this.getServerOptions()
  },
  activated() {
    this.getServerOptions();
  },
  methods: {
    getList() {
      this.loading = true
      listNode(this.queryParams).then(response => {
        this.nodeList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: null,
        userName: null,
        tableId: null,
        serverName: null,
        networkName: null,
        password: null,
        nodeName: null,
        networkIp: null,
        explanation: null,
        createTime: null,
        nodeStatus: null,
        status: null
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.currentServer = null
      this.form.serverName = ''
      this.open = true
      this.title = "添加节点"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getNode(id).then(response => {
        this.form = response.data
        this.onServerChange(this.form.serverName)
        this.open = true
        this.title = "修改节点"
      })
    },
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;

      this.form.userName = this.$store.getters.name;
      this.form.nodeStatus = this.form.nodeStatus || '0';
      this.form.status = this.form.status || '0';

      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateNode(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addNode(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          }
        } else {
          this.isSubmitting = false;
        }
      })
    },
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除节点编号为"' + ids + '"的数据项？').then(function() {
        return delNode(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('tinc/node/export', {
        ...this.queryParams
      }, `node_${new Date().getTime()}.xlsx`)
    },

    getServerOptions() {
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.serverOptions = response.rows
      }).catch(error => {
        console.error('获取服务器列表失败:', error)
        this.$modal.msgError('获取服务器列表失败')
      })
    },
    onServerChange(serverName) {
      const server = this.serverOptions.find(s => s.serverName === serverName)
      this.currentServer = server
      this.form.networkName = ''
      this.networkOptions = []

      if(server){
        this.loadNetworkOptions(server.serverName)
      }
    },
    loadNetworkOptions(serverName){
      listNetwork({serverName : serverName}).then(response =>{
        this.networkOptions = response.rows
      }).catch(error =>{
        console.error('获取内网列表失败:', error)
        this.$modal.msgError('获取内网列表失败')
      })
    },
    onNetworkChange(networkName) {
      const network = this.networkOptions.find(n => n.networkName === networkName);

      if (!network) {
        return;
      }

      if (network.segment) {
        try {
          const segmentIP = network.segment.split('/')[0];
          const ipParts = segmentIP.split('.');

          if (ipParts.length >= 3) {
            const autoIpPrefix = `${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.`;
            this.form.networkIp = autoIpPrefix;
            this.$forceUpdate();
          }
        } catch (e) {
          console.error('解析 segment 出错:', e);
        }
      }
    },
  }
}
</script>

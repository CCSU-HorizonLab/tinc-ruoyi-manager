<template>
  <div class="app-container">
    <!-- 页面标准 Header -->
    <div class="tl-page-header">
      <div class="tl-header-left">
        <div class="tl-header-icon">
          <i class="el-icon-cpu"></i>
        </div>
        <div class="tl-header-titles">
          <h2 class="tl-header-title">网络节点管理</h2>
          <p class="tl-header-desc">管理虚拟网络中的终端设备身份、IP 地址分配、网关归属与配置状态</p>
        </div>
      </div>
      <div class="tl-header-actions">
        <el-button
          type="primary"
          icon="el-icon-plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['node_mange:node_mange:add']"
        >新增节点</el-button>
        <el-button
          icon="el-icon-refresh"
          size="small"
          @click="getList"
        >刷新列表</el-button>
      </div>
    </div>

    <!-- 搜索筛选卡片 -->
    <div class="tl-filter-card" v-show="showSearch">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="所属用户" prop="userName">
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
        <el-form-item label="内网IP" prop="networkIp">
          <el-input
            v-model="queryParams.networkIp"
            placeholder="请输入内网IP"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh-left" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格与操作卡片 -->
    <div class="tl-table-card">
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
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="ID" align="center" prop="id" width="70">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点名称" align="left" prop="nodeName" min-width="140">
          <template slot-scope="scope">
            <div class="node-name-cell">
              <i class="el-icon-cpu node-cell-icon"></i>
              <span class="node-name-text">{{ scope.row.nodeName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属网络 / 网关" align="left" min-width="180">
          <template slot-scope="scope">
            <div class="network-hierarchy-cell">
              <span class="network-title">{{ scope.row.networkName }}</span>
              <span class="server-subtitle">
                <i class="el-icon-office-building"></i> {{ scope.row.serverName }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内网IP" align="center" prop="networkIp" min-width="130">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.networkIp }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备ID" align="center" prop="tableId" min-width="140">
          <template slot-scope="scope">
            <span class="tl-code-badge" :title="scope.row.tableId">{{ scope.row.tableId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配置状态" align="center" prop="status" width="110">
          <template slot-scope="scope">
            <span v-if="scope.row.status === '已配置' || scope.row.status === '1'" class="tl-status-badge configured">
              <span class="tl-status-dot"></span>已配置
            </span>
            <span v-else class="tl-status-badge unconfigured">
              <span class="tl-status-dot"></span>未配置
            </span>
          </template>
        </el-table-column>
        <el-table-column label="节点状态" align="center" prop="nodeStatus" width="110">
          <template slot-scope="scope">
            <span v-if="scope.row.nodeStatus === '1' || scope.row.nodeStatus === 'ONLINE' || scope.row.nodeStatus === '在线'" class="tl-status-badge online">
              <span class="tl-status-dot"></span>在线
            </span>
            <span v-else-if="scope.row.nodeStatus === 'OFFLINE' || scope.row.nodeStatus === '离线'" class="tl-status-badge offline">
              <span class="tl-status-dot"></span>离线
            </span>
            <span v-else class="tl-status-badge unknown" title="当前未启用节点心跳探针">
              <span class="tl-status-dot"></span>未知
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="160">
          <template slot-scope="scope">
            <span class="time-text">{{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="130">
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

        <!-- 空状态插槽 -->
        <template slot="empty">
          <div class="tl-empty-state">
            <div class="tl-empty-icon">
              <i class="el-icon-cpu"></i>
            </div>
            <div class="tl-empty-title">暂无节点记录</div>
            <div class="tl-empty-desc">当前虚拟网络中尚未注册任何客户端设备节点，请点击下方按钮新建节点</div>
            <el-button
              type="primary"
              icon="el-icon-plus"
              size="small"
              @click="handleAdd"
              v-hasPermi="['node_mange:node_mange:add']"
            >新增节点</el-button>
          </div>
        </template>
      </el-table>

      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </div>

    <!-- 添加或修改节点管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="95px">
        <el-form-item label="接入服务器" prop="selectedServerId">
          <el-select v-model="form.selectedServerId" placeholder="请选择接入服务器" @change="onServerChange" :disabled="form.id != null" style="width: 100%;">
            <el-option v-for="server in serverOptions" :key="server.id" :label="server.serverName" :value="server.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="所属内网" prop="networkId">
          <el-select v-model="form.networkId" placeholder="请选择所属内网" :disabled="!form.selectedServerId || form.id != null" @change="onNetworkChange" style="width: 100%;">
            <el-option v-for="network in networkOptions" :key="network.id" :label="network.networkName" :value="network.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="请输入节点名称" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="内网IP" prop="networkIp">
          <el-input v-model="form.networkIp" placeholder="请输入内网IP (如 10.0.11.2)" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入安全密码" />
        </el-form-item>
        <el-form-item label="备注" prop="explanation">
          <el-input v-model="form.explanation" type="textarea" :rows="2" placeholder="请输入备注说明" />
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
        selectedServerId: [
          { required: true, message: "接入服务器不能为空", trigger: "blur" }
        ],
        networkId: [
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
        networkId: null,
        selectedServerId: null,
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
      this.form.selectedServerId = null
      this.open = true
      this.title = "添加节点"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getNode(id).then(response => {
        this.form = response.data
        const server = this.serverOptions.find(s => s.serverName === this.form.serverName)
        this.$set(this.form, 'selectedServerId', server ? server.id : null)
        this.onServerChange(this.form.selectedServerId, false)
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
      this.$modal.confirm('是否确认删除节点编号为"' + ids + '"？后端将先撤销 hosts 授权、重载并验证网络，再删除管理记录。').then(function() {
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
    onServerChange(serverId, clearNetwork = true) {
      const server = this.serverOptions.find(s => String(s.id) === String(serverId))
      this.currentServer = server
      this.form.serverName = server ? server.serverName : null
      if (clearNetwork) {
        this.form.networkId = null
        this.form.networkName = null
      }
      this.networkOptions = []

      if(server){
        this.loadNetworkOptions(server.id)
      }
    },
    loadNetworkOptions(serverId){
      listNetwork({serverId : serverId}).then(response =>{
        this.networkOptions = response.rows
      }).catch(error =>{
        console.error('获取内网列表失败:', error)
        this.$modal.msgError('获取内网列表失败')
      })
    },
    onNetworkChange(networkId) {
      const network = this.networkOptions.find(n => String(n.id) === String(networkId));

      if (!network) {
        return;
      }
      this.form.networkName = network.networkName;

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

<style scoped>
.node-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-cell-icon {
  color: #0284c7;
  font-size: 15px;
}

.node-name-text {
  font-weight: 600;
  color: #0f172a;
}

.network-hierarchy-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.network-title {
  font-weight: 500;
  color: #0f172a;
}

.server-subtitle {
  font-size: 11px;
  color: #64748b;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}
</style>

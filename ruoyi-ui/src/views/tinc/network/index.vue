<template>
  <div class="app-container">
    <!-- 页面标准 Header -->
    <div class="tl-page-header">
      <div class="tl-header-left">
        <div class="tl-header-icon">
          <i class="el-icon-connection"></i>
        </div>
        <div class="tl-header-titles">
          <h2 class="tl-header-title">虚拟网络管理</h2>
          <p class="tl-header-desc">管理 Tinc Network 与 Access Server 的资源归属、网段拓扑及运行时就绪状态</p>
        </div>
      </div>
      <div class="tl-header-actions">
        <el-button
          type="primary"
          icon="el-icon-plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:add']"
        >新建网络</el-button>
        <el-button
          icon="el-icon-refresh"
          size="small"
          @click="refreshAllRuntime"
        >批量探活</el-button>
      </div>
    </div>

    <!-- 搜索筛选卡片 -->
    <div class="tl-filter-card" v-show="showSearch">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="接入服务器" prop="serverName">
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

      <el-table v-loading="loading" :data="networkList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="ID" align="center" prop="id" width="70">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建者" align="center" prop="rootName" width="110">
          <template slot-scope="scope">
            <span>{{ scope.row.rootName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接入服务器" align="left" prop="serverName" min-width="140">
          <template slot-scope="scope">
            <div class="server-title-cell">
              <i class="el-icon-office-building server-cell-icon"></i>
              <span>{{ scope.row.serverName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内网名称" align="left" prop="networkName" min-width="150">
          <template slot-scope="scope">
            <span class="network-name-text">{{ scope.row.networkName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="网段" align="center" prop="segment" min-width="130">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.segment }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点数量" align="center" prop="nodes" width="120">
          <template slot-scope="scope">
            <span class="tl-count-badge">
              <i class="el-icon-connection"></i> {{ scope.row.nodes || 0 }} 个节点
            </span>
          </template>
        </el-table-column>
        <el-table-column label="运行状态" align="center" min-width="130">
          <template slot-scope="scope">
            <span v-if="isStatusLoading(scope.row.id)" class="tl-status-badge checking">
              <i class="el-icon-loading"></i> 检查中
            </span>
            <span v-else-if="runtimeLabel(scope.row.id) === 'READY'" class="tl-status-badge ready">
              <span class="tl-status-dot"></span>READY
            </span>
            <span v-else-if="isStatusUnknown(scope.row.id)" class="tl-status-badge info">
              <span class="tl-status-dot"></span>{{ runtimeLabel(scope.row.id) }}
            </span>
            <span v-else class="tl-status-badge danger">
              <span class="tl-status-dot"></span>{{ runtimeLabel(scope.row.id) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="160">
          <template slot-scope="scope">
            <span class="time-text">{{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="150">
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
            <el-button
              size="mini"
              type="text"
              icon="el-icon-refresh"
              @click="loadRuntimeStatus(scope.row.id)"
              v-hasPermi="['TincNetworkMange:TincNetworkMange:query']"
            >刷新状态</el-button>
          </template>
        </el-table-column>

        <!-- 空状态插槽 -->
        <template slot="empty">
          <div class="tl-empty-state">
            <div class="tl-empty-icon">
              <i class="el-icon-connection"></i>
            </div>
            <div class="tl-empty-title">暂无虚拟网络</div>
            <div class="tl-empty-desc">当前接入服务器下尚未创建任何虚拟网络，请点击下方按钮新建虚拟网络</div>
            <el-button
              type="primary"
              icon="el-icon-plus"
              size="small"
              @click="handleAdd"
              v-hasPermi="['TincNetworkMange:TincNetworkMange:add']"
            >新建网络</el-button>
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

    <!-- 添加或修改内网管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="95px">
        <el-form-item label="接入服务器" prop="serverId">
          <el-select v-model="form.serverId" placeholder="请选择接入服务器" @change="onServerChange" style="width: 100%;">
            <el-option v-for="server in serverOptions" :key="server.id" :label="server.serverName" :value="server.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="内网名称" prop="networkName">
          <el-input v-model="form.networkName" placeholder="请输入内网名称" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="网段" prop="segment">
          <el-select v-model="form.segment" placeholder="请选择网段" :disabled="!currentServer" style="width: 100%;">
            <el-option v-for="segment in segmentOptions" :key="segment.value" :label="segment.label" :value="segment.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-select v-model="form.port" placeholder="请选择端口" :disabled="!currentServer" style="width: 100%;">
            <el-option v-for="port in portOptions" :key="port.value" :label="port.label" :value="port.value"></el-option>
          </el-select>
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
import { listNetwork, getNetwork, getNetworkRuntime, delNetwork, addNetwork, updateNetwork } from "@/api/tinc/network"
import { listServer } from "@/api/tinc/server"

export default {
  name: "Network",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      networkList: [],
      runtimeStatus: {},
      title: "",
      open: false,
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
      form: {},
      rules: {
        serverId: [
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
      isSubmitting: false,
      serverOptions: [],
      currentServer: null,
      segmentOptions: [],
      portOptions: []
    }
  },
  created() {
    this.getList()
    this.getServerOptions();
  },
  activated() {
    this.getServerOptions();
  },
  methods: {
    getList() {
      this.loading = true
      listNetwork(this.queryParams).then(response => {
        this.networkList = response.rows
        this.total = response.total
        this.loadRuntimeStatuses(response.rows)
        this.loading = false
      })
    },
    loadRuntimeStatuses(networks) {
      ;(networks || []).forEach(network => this.loadRuntimeStatus(network.id, false))
    },
    refreshAllRuntime() {
      this.loadRuntimeStatuses(this.networkList)
      this.$modal.msgSuccess('已触发全量网络运行状态探活')
    },
    loadRuntimeStatus(networkId, notify = true) {
      if (!networkId) return
      this.$set(this.runtimeStatus, networkId, { loading: true })
      getNetworkRuntime(networkId).then(response => {
        if (!response || !response.data) {
          throw new Error('服务端未返回运行状态')
        }
        this.$set(this.runtimeStatus, networkId, response.data)
        if (notify) this.$modal.msgSuccess('运行状态已刷新')
      }).catch(error => {
        this.$set(this.runtimeStatus, networkId, {
          readiness: 'UNKNOWN',
          failureCode: 'STATUS_QUERY_FAILED',
          failureMessage: error && error.message ? error.message : '运行状态查询失败'
        })
        if (notify) this.$modal.msgError('运行状态查询失败')
      })
    },
    isStatusLoading(networkId) {
      const status = this.runtimeStatus[networkId]
      return !status || status.loading
    },
    isStatusUnknown(networkId) {
      const status = this.runtimeStatus[networkId]
      return status && status.readiness === 'UNKNOWN'
    },
    runtimeLabel(networkId) {
      const status = this.runtimeStatus[networkId]
      if (!status || status.loading) return '检查中'
      if (status.readiness === 'READY') return 'READY'
      if (status.readiness === 'UNKNOWN') return status.failureCode || 'UNKNOWN'
      return status.failureCode || status.readiness || 'NOT READY'
    },
    runtimeTagType(networkId) {
      const status = this.runtimeStatus[networkId]
      if (!status || status.loading) return 'info'
      if (status.readiness === 'UNKNOWN') return 'info'
      return status.readiness === 'READY' ? 'success' : 'danger'
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: null,
        serverId: null,
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
      this.segmentOptions = []
      this.portOptions = []
      this.open = true
      this.title = "添加内网"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getNetwork(id).then(response => {
        this.form = response.data
        const savedPort = this.form.port;
        const savedSegment = this.form.segment;
        this.onServerChange(this.form.serverId);
        this.form.port = savedPort;
        this.form.segment = savedSegment;
        this.open = true
        this.title = "修改内网"
      })
    },
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      this.form.rootName = this.$store.getters.name;

      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateNetwork(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).catch(error => {
              this.$modal.msgError(`修改失败: ${error.response?.data?.msg || error.message}`)
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addNetwork(this.form).then(response => {
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
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除内网编号为"' + ids + '"的管理记录？正在运行或仍有关联节点的网络不能删除；本操作不会停止 Tinc，也不会删除 /etc/tinc 配置和私钥。').then(function() {
        return delNetwork(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('tinc/network/export', {
        ...this.queryParams
      }, `network_${new Date().getTime()}.xlsx`)
    },

    getServerOptions(){
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.serverOptions = response.rows;
      }).catch(error => {
        console.error('获取服务器列表失败:', error);
        this.$modal.msgError('获取服务器列表失败');
      });
    },

    onServerChange(serverId){
      const server = this.serverOptions.find(s => String(s.id) === String(serverId));
      this.currentServer = server;
      this.form.serverName = server ? server.serverName : null;

      this.form.port = '';
      this.form.segment = '';

      if(server){
        try {
          this.segmentOptions = this.generateSegmentOptions(server.startSegment, server.endSegment);
          this.portOptions = this.generatePortOptions(server.startPort, server.endPort);
        } catch (error) {
          console.error('生成选项失败:', error);
          this.segmentOptions = [];
          this.portOptions = [];
        }
      }else{
        this.segmentOptions = [];
        this.portOptions = [];
      }
    },

    generateSegmentOptions(startSegment, endSegment) {
      const segments = [];
      if (!startSegment || !endSegment) return segments;

      try {
        const startParts = startSegment.split('.');
        const endParts = endSegment.split('.');

        if (startParts.length === 3 && endParts.length === 3 &&
            startParts[0] === endParts[0] && startParts[1] === endParts[1]) {

          const start = parseInt(startParts[2]);
          const end = parseInt(endParts[2]);

          if (!isNaN(start) && !isNaN(end) && start <= end) {
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

    generatePortOptions(startPort, endPort) {
      const ports = [];
      if (!startPort || !endPort) return ports;

      try {
        const start = parseInt(startPort);
        const end = parseInt(endPort);

        if (!isNaN(start) && !isNaN(end) && start <= end) {
          const maxPorts = 100;
          const actualEnd = Math.min(end, start + maxPorts);

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

    validatePort(rule, value, callback) {
      if (!value) {
        return callback(new Error('端口不能为空'));
      }

      if (this.currentServer) {
        const port = parseInt(value);
        const startPort = parseInt(this.currentServer.startPort);
        const endPort = parseInt(this.currentServer.endPort);

        if (isNaN(port) || port < startPort || port > endPort) {
          return callback(new Error(`端口必须在 ${startPort} 到 ${endPort} 之间`));
        }
      }

      callback();
    },

    validateSegment(rule, value, callback) {
      if (!value) {
        return callback(new Error('网段不能为空'));
      }

      if (this.currentServer) {
        const startSegment = this.currentServer.startSegment;
        const endSegment = this.currentServer.endSegment;

        const startParts = startSegment.split('.');
        const endParts = endSegment.split('.');
        const valueParts = value.split('.');

        if (startParts.length === 3 && endParts.length === 3 && valueParts.length === 3) {
          if (startParts[0] === endParts[0] && startParts[0] === valueParts[0] &&
              startParts[1] === endParts[1] && startParts[1] === valueParts[1]) {

            const start = parseInt(startParts[2]);
            const end = parseInt(endParts[2]);
            const valueNum = parseInt(valueParts[2]);

            if (isNaN(valueNum) || valueNum < start || valueNum > end) {
              return callback(new Error(`网段必须在 ${startSegment} 到 ${endSegment} 之间`));
            }
          }
        }
      }

      callback();
    }
  }
}
</script>

<style scoped>
.server-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #334155;
}

.server-cell-icon {
  color: #0284c7;
  font-size: 15px;
}

.network-name-text {
  font-weight: 600;
  color: #0f172a;
}

.time-text {
  font-size: 12px;
  color: #64748b;
}
</style>

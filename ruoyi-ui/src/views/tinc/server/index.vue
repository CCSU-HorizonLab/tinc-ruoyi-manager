<template>
  <div class="app-container">
    <!-- 页面标准 Header -->
    <div class="tl-page-header">
      <div class="tl-header-left">
        <div class="tl-header-icon">
          <i class="el-icon-coin"></i>
        </div>
        <div class="tl-header-titles">
          <h2 class="tl-header-title">接入服务器管理</h2>
          <p class="tl-header-desc">统一管理本地网关与分布式远程 Access Server 接入集群及探针状态</p>
        </div>
      </div>
      <div class="tl-header-actions">
        <el-button
          type="primary"
          icon="el-icon-plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['manger:manger:add']"
        >新增服务器</el-button>
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
        <el-form-item label="服务器名称" prop="serverName">
          <el-input
            v-model="queryParams.serverName"
            placeholder="请输入服务器名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="服务器IP" prop="serverIp">
          <el-input
            v-model="queryParams.serverIp"
            placeholder="请输入服务器IP"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="内网数量" prop="number">
          <el-input
            v-model="queryParams.number"
            placeholder="请输入内网数量"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option
              v-for="dict in dict.type.server_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
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
            v-hasPermi="['manger:manger:add']"
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
            v-hasPermi="['manger:manger:edit']"
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
            v-hasPermi="['manger:manger:remove']"
          >删除</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            plain
            icon="el-icon-download"
            size="mini"
            @click="handleExport"
            v-hasPermi="['manger:manger:export']"
          >导出</el-button>
        </el-col>
        <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="serverList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="ID" align="center" prop="id" width="70">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="服务器名称" align="left" prop="serverName" min-width="140">
          <template slot-scope="scope">
            <div class="server-title-cell">
              <i class="el-icon-office-building server-cell-icon"></i>
              <span class="server-title-name">{{ scope.row.serverName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="服务器IP" align="center" prop="serverIp" min-width="130">
          <template slot-scope="scope">
            <span class="tl-code-badge">{{ scope.row.serverIp }}</span>
          </template>
        </el-table-column>
        <el-table-column label="运行模式" align="center" prop="runtimeType" width="110">
          <template slot-scope="scope">
            <span v-if="scope.row.runtimeType === 'LOCAL'" class="tl-status-badge local">
              <span class="tl-status-dot"></span>LOCAL
            </span>
            <span v-else class="tl-status-badge info" style="background:#e0f2fe; color:#0369a1; border-color:#bae6fd;">
              <span class="tl-status-dot" style="background:#0284c7;"></span>AGENT
            </span>
          </template>
        </el-table-column>
        <el-table-column label="内网数量" align="center" prop="number" width="110">
          <template slot-scope="scope">
            <span class="tl-count-badge">
              <i class="el-icon-share"></i> {{ scope.row.number || 0 }} 个内网
            </span>
          </template>
        </el-table-column>
        <el-table-column label="Agent状态" align="center" prop="agentStatus" width="130">
          <template slot-scope="scope">
            <span v-if="scope.row.agentStatus === 'ONLINE' || scope.row.agentStatus === 'LOCAL'" class="tl-status-badge online">
              <span class="tl-status-dot"></span>{{ scope.row.agentStatus }}
            </span>
            <span v-else-if="scope.row.agentStatus === 'DEGRADED'" class="tl-status-badge degraded">
              <span class="tl-status-dot"></span>DEGRADED
            </span>
            <span v-else class="tl-status-badge unreachable">
              <span class="tl-status-dot"></span>{{ scope.row.agentStatus || 'UNREACHABLE' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" prop="agentVersion" width="90">
          <template slot-scope="scope">
            <span v-if="scope.row.agentVersion" class="tl-code-badge">v{{ scope.row.agentVersion }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="CPU / 内存" align="center" width="170">
          <template slot-scope="scope">
            <div class="metrics-pill-wrap" v-if="scope.row.agentCpuUsage != null || scope.row.agentMemoryUsage != null">
              <span class="metric-pill cpu" :class="getUsageClass(scope.row.agentCpuUsage)">
                CPU {{ scope.row.agentCpuUsage != null ? scope.row.agentCpuUsage + '%' : '-' }}
              </span>
              <span class="metric-pill mem" :class="getUsageClass(scope.row.agentMemoryUsage)">
                MEM {{ scope.row.agentMemoryUsage != null ? scope.row.agentMemoryUsage + '%' : '-' }}
              </span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="最后探针" align="center" prop="agentLastSeen" width="160">
          <template slot-scope="scope">
            <span class="seen-time-text">{{ parseTime(scope.row.agentLastSeen) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="150">
          <template slot-scope="scope">
            <el-button
              size="mini"
              type="text"
              icon="el-icon-edit"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['manger:manger:edit']"
            >修改</el-button>
            <el-button
              size="mini"
              type="text"
              icon="el-icon-delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['manger:manger:remove']"
            >删除</el-button>
            <el-button
              v-if="scope.row.runtimeType === 'AGENT'"
              size="mini"
              type="text"
              icon="el-icon-refresh"
              @click="handleProbe(scope.row)"
              v-hasPermi="['manger:manger:query']"
            >探测Agent</el-button>
          </template>
        </el-table-column>

        <!-- 空状态自定义插槽 -->
        <template slot="empty">
          <div class="tl-empty-state">
            <div class="tl-empty-icon">
              <i class="el-icon-coin"></i>
            </div>
            <div class="tl-empty-title">暂无接入服务器</div>
            <div class="tl-empty-desc">当前系统尚未注册 Access Server 节点，请点击下方按钮新增接入服务器</div>
            <el-button
              type="primary"
              icon="el-icon-plus"
              size="small"
              @click="handleAdd"
              v-hasPermi="['manger:manger:add']"
            >新增服务器</el-button>
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

    <!-- 添加或修改服务器管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="服务器名称" prop="serverName">
          <el-input v-model="form.serverName" placeholder="请输入服务器名字" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="服务器IP" prop="serverIp">
          <el-input v-model="form.serverIp" placeholder="请输入服务器ip" />
        </el-form-item>
        <el-form-item label="运行模式" prop="runtimeType">
          <el-radio-group v-model="form.runtimeType">
            <el-radio label="LOCAL">本机 (LOCAL)</el-radio>
            <el-radio label="AGENT">Access Agent</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.runtimeType === 'AGENT'">
          <el-form-item label="Agent端口" prop="agentPort">
            <el-input-number v-model="form.agentPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="Agent密钥" prop="agentSecret">
            <el-input v-model="form.agentSecret" type="password" show-password
                      :placeholder="form.id ? '留空表示保持原密钥' : '请输入至少32位随机密钥'" />
          </el-form-item>
        </template>
        <el-form-item label="起始网段" prop="startSegment">
          <el-input v-model="form.startSegment" placeholder="请输入起始网段 (如 10.0.10)" />
        </el-form-item>
        <el-form-item label="终止网段" prop="endSegment">
          <el-input v-model="form.endSegment" placeholder="请输入终止网段 (如 10.0.20)" />
        </el-form-item>
        <el-form-item label="起始端口" prop="startPort">
          <el-input v-model="form.startPort" placeholder="请输入起始端口 (如 600)" />
        </el-form-item>
        <el-form-item label="终止端口" prop="endPort">
          <el-input v-model="form.endPort" placeholder="请输入终止端口 (如 655)" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注说明" />
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
import { listServer, getServer, delServer, addServer, updateServer, probeServer } from "@/api/tinc/server"

export default {
  name: "Server",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      serverList: [],
      dict: {
        type: {
          server_status: [
            { value: '0', label: '离线' },
            { value: '1', label: '在线' }
          ]
        }
      },
      title: "",
      isSubmitting: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        serverName: null,
        serverIp: null,
        number: null,
        status: null
      },
      form: {},
      rules: {
        serverName: [
          { required: true, message: "服务器名字不能为空", trigger: "blur" }
        ],
        serverIp: [
          { required: true, message: "服务器ip不能为空", trigger: "blur" }
        ],
        startSegment: [
          { required: true, message: "起始网段不能为空", trigger: "blur" }
        ],
        endSegment: [
          { required: true, message: "终止网段不能为空", trigger: "blur" }
        ],
        startPort: [
          { required: true, message: "起始端口不能为空", trigger: "blur" }
        ],
        endPort: [
          { required: true, message: "终止端口不能为空", trigger: "blur" }
        ],
        number: [
          { required: true, message: "内网数量不能为空", trigger: "blur" }
        ],
        status: [
          { required: true, message: "状态不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listServer(this.queryParams).then(response => {
        this.serverList = response.rows
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
        serverName: null,
        serverIp: null,
        startSegment: null,
        endSegment: null,
        startPort: null,
        endPort: null,
        remark: null,
        number: null,
        status: null,
        runtimeType: 'LOCAL',
        agentPort: 9088,
        agentSecret: null
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
      this.open = true
      this.title = "添加服务器"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getServer(id).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改服务器"
      })
    },
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateServer(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addServer(this.form).then(response => {
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
      this.$modal.confirm('是否确认删除服务器编号为"' + ids + '"的管理记录？仍有关联网络或节点时后端会拒绝删除。').then(function() {
        return delServer(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleProbe(row) {
      probeServer(row.id).then(response => {
        this.$modal.msgSuccess('Agent 状态：' + (response.data.agentStatus || 'UNREACHABLE'))
        this.getList()
      })
    },
    handleExport() {
      this.download('tinc/server/export', {
        ...this.queryParams
      }, `server_${new Date().getTime()}.xlsx`)
    },
    getUsageClass(val) {
      const num = parseFloat(val);
      if (isNaN(num)) return '';
      if (num >= 80) return 'high';
      if (num >= 60) return 'medium';
      return 'normal';
    }
  }
}
</script>

<style scoped>
.server-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-cell-icon {
  color: #0284c7;
  font-size: 15px;
}

.server-title-name {
  font-weight: 600;
  color: #0f172a;
}

.metrics-pill-wrap {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.metric-pill {
  display: inline-block;
  font-family: monospace;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.metric-pill.normal {
  background-color: #ecfdf5;
  color: #065f46;
  border-color: #a7f3d0;
}

.metric-pill.medium {
  background-color: #fffbeb;
  color: #92400e;
  border-color: #fde68a;
}

.metric-pill.high {
  background-color: #fef2f2;
  color: #991b1b;
  border-color: #fecaca;
}

.seen-time-text {
  font-size: 12px;
  color: #64748b;
}

.text-muted {
  color: #94a3b8;
}
</style>

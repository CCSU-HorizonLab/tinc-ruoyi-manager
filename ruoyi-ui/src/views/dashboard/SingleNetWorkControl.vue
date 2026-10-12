<template>
  <div class="single-network-control" v-loading="loading">
    <!-- 统一标准 Header -->
    <div class="tl-page-header">
      <div class="tl-header-left">
        <div class="tl-header-icon">
          <i class="el-icon-monitor"></i>
        </div>
        <div class="tl-header-titles">
          <h2 class="tl-header-title">单网监控面板</h2>
          <p class="tl-header-desc">按稳定 ID 查询网关运行状态；未采集的指标不显示模拟数据</p>
        </div>
      </div>
      <div class="tl-header-actions" v-if="selectedNetwork">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新(30s)"
          @change="handleAutoRefreshChange">
        </el-switch>
        <el-button 
          type="primary" 
          icon="el-icon-refresh" 
          size="small" 
          @click="getMonitorData"
          style="margin-left: 15px;">
          手动刷新
        </el-button>
        <el-button 
          size="small" 
          icon="el-icon-switch-button"
          @click="resetSelection"
          style="margin-left: 10px;">
          重新选择
        </el-button>
      </div>
    </div>
    
    <!-- 未选定网络：选择引导与概览区域 -->
    <div v-if="!selectedNetwork">
      <!-- 选择控制卡片 -->
      <div class="tl-card selection-card">
        <div class="selection-card-header">
          <div class="step-badge">第一步</div>
          <h3 class="selection-title">选择要监控的虚拟网络</h3>
          <p class="selection-desc">从已注册的 Access Server 中选择目标节点及所属虚拟网络以载入监控详情</p>
        </div>

        <el-form :inline="true" class="network-select-form">
          <el-form-item label="接入服务器">
            <el-select v-model="selectedServer" placeholder="请选择接入服务器" @change="handleServerChange" filterable style="width: 220px;">
              <el-option
                v-for="server in servers"
                :key="server.value"
                :label="server.label"
                :value="server.value">
                <i class="el-icon-office-building" style="margin-right: 6px; color: #0284c7;"></i>
                <span>{{ server.label }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="虚拟网络">
            <el-select v-model="selectedNetworkId" placeholder="请选择虚拟网络" :disabled="!selectedServer" filterable style="width: 220px;">
              <el-option
                v-for="network in networks"
                :key="network.value"
                :label="network.label"
                :value="network.value">
                <i class="el-icon-connection" style="margin-right: 6px; color: #10b981;"></i>
                <span>{{ network.label }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button 
              type="primary" 
              icon="el-icon-video-play" 
              @click="confirmSelection" 
              :disabled="!selectedServer || !selectedNetworkId">
              进入实时监控
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 空状态与专业架构引导 (填充空白，严禁造假数据) -->
      <div class="tl-card empty-guidance-card">
        <div class="guidance-empty-illustration">
          <div class="radar-box">
            <i class="el-icon-data-line"></i>
          </div>
          <h4 class="guidance-main-tip">选择一个 Access Server 和 Network 以查看实时运行状态</h4>
          <p class="guidance-sub-tip">监控面板只读查询对应网关的 Tinc Runtime；流量、延迟与节点在线率暂未采集</p>
        </div>

        <div class="guidance-features-grid">
          <div class="guidance-feature-item">
            <div class="item-icon-wrap icon-blue">
              <i class="el-icon-cpu"></i>
            </div>
            <div class="item-content">
              <div class="item-title">接入服务器绑定</div>
              <div class="item-desc">精准定位虚拟网络的宿主网关，验证本地运行时或远程 Agent 通信链路</div>
            </div>
          </div>

          <div class="guidance-feature-item">
            <div class="item-icon-wrap icon-green">
              <i class="el-icon-lock"></i>
            </div>
            <div class="item-content">
              <div class="item-title">安全隧道与公钥路由</div>
              <div class="item-desc">配置状态仅说明公钥应用记录，不代表客户端已在线或隧道已连通</div>
            </div>
          </div>

          <div class="guidance-feature-item">
            <div class="item-icon-wrap icon-purple">
              <i class="el-icon-aim"></i>
            </div>
            <div class="item-content">
              <div class="item-title">实时探针与健康就绪</div>
              <div class="item-desc">只读探测运行时就绪度，不启动、重启或重载 Tinc</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 网络监控详情区域 (已选定网络) -->
    <div v-else>
      <!-- 网络基本信息横幅 -->
      <div class="tl-card network-info-banner">
        <div class="info-left">
          <div class="banner-badge">
            <i class="el-icon-connection"></i>
          </div>
          <div class="banner-meta">
            <div class="banner-title-row">
              <span class="label">当前监控内网:</span>
              <span class="value network-name-highlight">{{ selectedNetworkName }}</span>
              <el-divider direction="vertical"></el-divider>
              <span class="label">宿主服务器:</span>
              <span class="value"><i class="el-icon-office-building"></i> {{ selectedServerLabel }}</span>
            </div>
            <div class="banner-subtitle-row">
              <span class="sub-label">监控目标ID:</span>
              <span class="tl-code-badge">{{ selectedNetworkId }}</span>
              <span class="status-real-tag">真实运行时链路</span>
            </div>
          </div>
        </div>
        <div class="info-right">
          <span :class="['tl-status-badge', readiness === 'READY' ? 'ready' : 'unknown']" style="font-size: 13px; padding: 6px 14px;">
            <span class="tl-status-dot"></span>
            {{ statusLabel }}
          </span>
        </div>
      </div>
      
      <el-alert v-if="queryError" :title="queryError" type="error" :closable="false" show-icon />
      <el-card class="tl-card">
        <div slot="header">本次观测</div>
        <p>查询时间：{{ observedAtLabel }}</p>
        <p>诊断代码：{{ failureCode || '—' }}</p>
        <p>{{ failureMessage || 'READY 仅代表网关运行时就绪，不等同于客户端握手或 Ping 成功。' }}</p>
        <p>登记节点：{{ display(nodeSummary.total) }}；已配置：{{ display(nodeSummary.configured) }}。</p>
        <p>节点在线率未采集。数据库“已配置”或历史状态不能替代有效期内的心跳证据。</p>
      </el-card>
      <el-card class="chart-card tl-card" style="margin-top: 20px;">
        <div slot="header">性能与历史趋势</div>
        <el-empty description="未采集：响应时间、流量、健康评分、历史中断事件；不显示模拟值。" :image-size="70" />
      </el-card>
    </div>
  </div>
</template>

<script>
import { listServerOptions } from '@/api/tinc/server'
import { listNetwork } from '@/api/tinc/network'
import { getSingleNetworkMonitor } from '@/api/monitor/networkMonitor'

export default {
  data() {
    return {
      loading: false, autoRefresh: false, refreshTimer: null, requestVersion: 0,
      servers: [], networks: [], selectedServer: '', selectedNetworkId: '',
      selectedNetwork: false, selectedNetworkName: '', readiness: 'UNKNOWN',
      queryError: '', failureCode: '', failureMessage: '', observedAt: null,
      nodeSummary: { total: null, configured: null }
    }
  },
  computed: {
    selectedServerLabel() {
      const server = this.servers.find(item => item.value === this.selectedServer)
      return server ? server.label : '—'
    },
    observedAtLabel() {
      return this.observedAt ? new Date(this.observedAt).toLocaleString() : '未获得观测'
    },
    statusLabel() {
      const labels = { READY: 'READY · 网关就绪', NOT_READY: 'NOT_READY · 网关未就绪',
        DEGRADED: 'DEGRADED · 部分异常', UNKNOWN: 'UNKNOWN · 状态未知' }
      return labels[this.readiness] || labels.UNKNOWN
    }
  },
  created() { this.getServerOptions() },
  beforeDestroy() {
    this.requestVersion++
    this.stopRefreshTimer()
  },
  methods: {
    display(value) { return value == null ? '—' : value },
    getServerOptions() {
      return listServerOptions().then(response => {
        this.servers = response.rows.map(server => ({ value: server.id, label: server.serverName }))
      }).catch(() => { this.servers = [] })
    },
    getNetworksByServer(serverId) {
      return listNetwork({ serverId, pageNum: 1, pageSize: 10000 }).then(response => {
        if (this.selectedServer !== serverId) return
        this.networks = response.rows.map(network => ({ value: network.id, label: network.networkName }))
      }).catch(() => {
        if (this.selectedServer === serverId) this.networks = []
      })
    },
    handleServerChange(serverId) {
      this.requestVersion++
      this.selectedNetworkId = ''
      this.networks = []
      this.clearObservation()
      if (serverId) this.getNetworksByServer(serverId)
    },
    confirmSelection() {
      const network = this.networks.find(item => item.value === this.selectedNetworkId)
      if (!this.selectedServer || !network) return
      this.selectedNetworkName = network.label
      this.selectedNetwork = true
      this.getMonitorData()
    },
    clearObservation() {
      this.readiness = 'UNKNOWN'
      this.observedAt = null
      this.failureCode = ''
      this.failureMessage = ''
      this.queryError = ''
      this.nodeSummary = { total: null, configured: null }
    },
    async getMonitorData() {
      if (!this.selectedNetwork || this.loading) return
      const version = ++this.requestVersion
      const networkId = this.selectedNetworkId
      this.loading = true
      this.clearObservation()
      try {
        const response = await getSingleNetworkMonitor(networkId)
        if (version !== this.requestVersion || networkId !== this.selectedNetworkId) return
        const data = response.data
        if (!data || data.networkId !== networkId || !data.nodeSummary) throw new Error('INVALID_MONITOR_RESPONSE')
        this.readiness = ['READY', 'NOT_READY', 'DEGRADED'].includes(data.readiness) ? data.readiness : 'UNKNOWN'
        this.observedAt = data.observedAt
        this.failureCode = data.failureCode
        this.failureMessage = data.failureMessage
        this.nodeSummary = data.nodeSummary
      } catch (_) {
        if (version !== this.requestVersion) return
        this.clearObservation()
        this.queryError = 'STATUS_QUERY_FAILED · 查询失败，当前状态未知，请重试。'
        this.failureCode = 'STATUS_QUERY_FAILED'
      } finally {
        if (version === this.requestVersion) this.loading = false
      }
    },
    handleAutoRefreshChange(value) {
      this.stopRefreshTimer()
      if (value) this.refreshTimer = setInterval(() => this.getMonitorData(), 30000)
    },
    stopRefreshTimer() {
      if (this.refreshTimer) clearInterval(this.refreshTimer)
      this.refreshTimer = null
    },
    resetSelection() {
      this.requestVersion++
      this.loading = false
      this.stopRefreshTimer()
      this.autoRefresh = false
      this.selectedServer = ''
      this.selectedNetworkId = ''
      this.selectedNetwork = false
      this.selectedNetworkName = ''
      this.networks = []
      this.clearObservation()
    }
  }
}
</script>

<style lang="scss" scoped>
.single-network-control {
  padding: 20px 24px;
}

/* 选择卡片 */
.selection-card {
  padding: 28px 32px;
  margin-bottom: 24px;

  .selection-card-header {
    margin-bottom: 24px;

    .step-badge {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      color: #0284c7;
      background: #e0f2fe;
      padding: 2px 8px;
      border-radius: 4px;
      margin-bottom: 8px;
    }

    .selection-title {
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
      margin: 0 0 6px 0;
    }

    .selection-desc {
      font-size: 13px;
      color: #64748b;
      margin: 0;
    }
  }

  .network-select-form {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;

    .el-form-item {
      margin-bottom: 0;
    }
  }
}

/* 引导说明区域 */
.empty-guidance-card {
  padding: 36px 32px;

  .guidance-empty-illustration {
    text-align: center;
    padding-bottom: 30px;
    border-bottom: 1px solid #f1f5f9;
    margin-bottom: 30px;

    .radar-box {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      background: #f0f9ff;
      color: #0284c7;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      margin: 0 auto 16px auto;
    }

    .guidance-main-tip {
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 8px 0;
    }

    .guidance-sub-tip {
      font-size: 13px;
      color: #64748b;
      margin: 0 auto;
      max-width: 550px;
      line-height: 1.5;
    }
  }

  .guidance-features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 20px;

    .guidance-feature-item {
      display: flex;
      align-items: flex-start;
      gap: 14px;
      padding: 16px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;

      .item-icon-wrap {
        width: 36px;
        height: 36px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 18px;
        flex-shrink: 0;

        &.icon-blue {
          background: #e0f2fe;
          color: #0284c7;
        }

        &.icon-green {
          background: #d1fae5;
          color: #059669;
        }

        &.icon-purple {
          background: #ede9fe;
          color: #7c3aed;
        }
      }

      .item-content {
        .item-title {
          font-size: 14px;
          font-weight: 600;
          color: #0f172a;
          margin-bottom: 4px;
        }

        .item-desc {
          font-size: 12px;
          color: #64748b;
          line-height: 1.5;
        }
      }
    }
  }
}

/* 信息横幅 */
.network-info-banner {
  padding: 18px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;

  .info-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .banner-badge {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%);
      color: #0284c7;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .banner-meta {
      .banner-title-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 6px;

        .label {
          color: #64748b;
          font-size: 13px;
        }

        .value {
          color: #0f172a;
          font-weight: 600;
          font-size: 15px;
        }

        .network-name-highlight {
          color: #0284c7;
          font-size: 16px;
        }
      }

      .banner-subtitle-row {
        display: flex;
        align-items: center;
        gap: 8px;

        .sub-label {
          font-size: 12px;
          color: #94a3b8;
        }

        .status-real-tag {
          font-size: 11px;
          color: #059669;
          background: #d1fae5;
          padding: 1px 6px;
          border-radius: 4px;
        }
      }
    }
  }
}

/* 监控指标卡片 */
.monitor-card {
  height: 100%;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    color: #334155;
    font-size: 14px;
  }
}

.progress-wrapper {
  display: flex;
  justify-content: center;
  padding: 10px 0 16px;
  
  .progress-text {
    display: flex;
    flex-direction: column;
    align-items: center;
    
    .value {
      font-size: 22px;
      font-weight: bold;
      color: #0f172a;
    }
    .unit {
      font-size: 12px;
      color: #64748b;
    }
  }
}

.monitor-details {
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
  
  .detail-item {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 12px;
    
    .detail-label {
      color: #64748b;
    }
    .detail-value {
      color: #0f172a;
      font-weight: 600;
    }
    .detail-value.success { color: #10b981; }
    .detail-value.warning { color: #f59e0b; }
    .detail-value.danger { color: #ef4444; }
  }
}

/* 图表卡片 */
.chart-card {
  margin-top: 4px;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
    }
  }
}
</style>

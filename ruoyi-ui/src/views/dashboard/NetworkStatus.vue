<template>
  <div class="network-status-container" v-loading="loading">
    <!-- 统一标准 Header -->
    <div class="tl-page-header">
      <div class="tl-header-left">
        <div class="tl-header-icon">
          <i class="el-icon-data-analysis"></i>
        </div>
        <div class="tl-header-titles">
          <h2 class="tl-header-title">网络状态统计</h2>
          <p class="tl-header-desc">只读查询网关运行时；READY 不代表客户端隧道或端到端 Ping 已验证</p>
        </div>
      </div>
      <div class="tl-header-actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新(30s)"
          @change="handleAutoRefreshChange">
        </el-switch>
        <el-button 
          type="primary" 
          icon="el-icon-refresh" 
          size="small" 
          @click="getAllStats"
          style="margin-left: 15px;">
          手动刷新
        </el-button>
      </div>
    </div>
    
    <el-alert v-if="queryError" :title="queryError" type="error" :closable="false" show-icon />
    <div class="section-title-wrap">
      <span class="section-title"><i class="el-icon-circle-check" style="color: #10b981;"></i> 已登记资产与运行时状态</span>
      <span class="section-badge-real">查询完成时间：{{ observedAtLabel }}</span>
    </div>

    <el-row :gutter="16" class="asset-cards-row">
      <!-- 虚拟网络总数 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="tl-card real-asset-card">
          <div class="asset-card-inner">
            <div class="asset-icon icon-network">
              <i class="el-icon-connection"></i>
            </div>
            <div class="asset-info">
              <div class="asset-label">虚拟网络总数</div>
              <div class="asset-value">{{ display(realAssets.totalNetworks) }} <span class="unit">个</span></div>
              <div class="asset-sub">
                <span :class="['tl-status-badge', realAssets.readyNetworks > 0 ? 'ready' : 'unknown']" style="padding: 1px 8px; font-size: 11px;">
                  <span class="tl-status-dot"></span>{{ display(realAssets.readyNetworks) }} READY · 正常
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- Access Server 集群 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="tl-card real-asset-card">
          <div class="asset-card-inner">
            <div class="asset-icon icon-server">
              <i class="el-icon-coin"></i>
            </div>
            <div class="asset-info">
              <div class="asset-label">接入服务器集群</div>
              <div class="asset-value">{{ display(realAssets.totalServers) }} <span class="unit">台</span></div>
              <div class="asset-sub">
                <span :class="['tl-status-badge', realAssets.onlineAgents > 0 ? 'online' : 'unknown']" style="padding: 1px 8px; font-size: 11px;">
                  <span class="tl-status-dot"></span>{{ display(realAssets.onlineAgents) }} 上次探测 ONLINE（非实时）
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 客户端节点 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="tl-card real-asset-card">
          <div class="asset-card-inner">
            <div class="asset-icon icon-node">
              <i class="el-icon-cpu"></i>
            </div>
            <div class="asset-info">
              <div class="asset-label">网络设备节点</div>
              <div class="asset-value">{{ display(realAssets.totalNodes) }} <span class="unit">个</span></div>
              <div class="asset-sub">
                <span class="tl-status-badge configured" style="padding: 1px 8px; font-size: 11px;">
                  <span class="tl-status-dot"></span>{{ display(realAssets.configuredNodes) }} 已配置
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 综合拓扑健康度 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="tl-card real-asset-card">
          <div class="asset-card-inner">
            <div class="asset-icon icon-health">
              <i class="el-icon-odometer"></i>
            </div>
            <div class="asset-info">
              <div class="asset-label">已确认就绪比例</div>
              <div class="asset-value">{{ display(realAssets.onlineRate) }}<span class="unit">%</span></div>
              <div class="asset-sub">
                <span class="rate-status-tip">UNKNOWN 不算就绪；不代表端到端连通</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card class="tl-card">
      <div slot="header">本次运行时查询（只读）</div>
      <el-table :data="observations" empty-text="暂无有效观测">
        <el-table-column prop="networkId" label="网络 ID" width="100" />
        <el-table-column prop="networkName" label="网络名称" />
        <el-table-column prop="readiness" label="运行时状态" />
        <el-table-column prop="failureCode" label="诊断代码" />
        <el-table-column prop="failureMessage" label="说明" />
      </el-table>
      <p>未就绪：{{ display(notReadyNetworks) }}；查询未知：{{ display(unknownNetworks) }}。</p>
    </el-card>
    <el-card class="network-chart-card tl-card" style="margin-top: 20px;">
      <div slot="header">性能与历史趋势</div>
      <el-empty description="未采集：响应时间、流量、健康评分、故障恢复时间及历史趋势；不显示模拟值。" :image-size="70" />
    </el-card>
  </div>
</template>

<script>
import { getGlobalNetworkStats } from '@/api/monitor/networkMonitor'

const emptyAssets = () => ({
  totalNetworks: null, readyNetworks: null, totalServers: null,
  onlineAgents: null, totalNodes: null, configuredNodes: null, onlineRate: null
})

export default {
  data() {
    return {
      loading: false, autoRefresh: false, refreshTimer: null, disposed: false,
      realAssets: emptyAssets(), observations: [], observedAt: null,
      queryError: '', notReadyNetworks: null, unknownNetworks: null
    }
  },
  computed: {
    observedAtLabel() {
      return this.observedAt ? new Date(this.observedAt).toLocaleString() : '未获得观测'
    }
  },
  created() { this.getAllStats() },
  beforeDestroy() {
    this.disposed = true
    this.stopRefreshTimer()
  },
  methods: {
    display(value) { return value == null ? '—' : value },
    async getAllStats() {
      if (this.loading || this.disposed) return
      this.loading = true
      this.queryError = ''
      try {
        const response = await getGlobalNetworkStats()
        if (this.disposed) return
        const data = response.data
        if (!data || !Array.isArray(data.networks) || !data.nodeSummary) throw new Error('INVALID_MONITOR_RESPONSE')
        this.observations = data.networks
        this.observedAt = data.observedAt
        this.notReadyNetworks = data.notReadyNetworks
        this.unknownNetworks = data.unknownNetworks
        this.realAssets = {
          totalNetworks: data.totalNetworks, readyNetworks: data.onlineNetworks,
          totalServers: data.totalServers, onlineAgents: data.reportedOnlineAgents,
          totalNodes: data.nodeSummary.total, configuredNodes: data.nodeSummary.configured,
          onlineRate: data.onlineRate
        }
      } catch (_) {
        if (this.disposed) return
        this.realAssets = emptyAssets()
        this.observations = []
        this.observedAt = null
        this.notReadyNetworks = null
        this.unknownNetworks = null
        this.queryError = 'STATUS_QUERY_FAILED · 查询失败，当前状态未知，请重试。'
      } finally {
        if (!this.disposed) this.loading = false
      }
    },
    handleAutoRefreshChange(value) {
      this.stopRefreshTimer()
      if (value) this.refreshTimer = setInterval(() => this.getAllStats(), 30000)
    },
    stopRefreshTimer() {
      if (this.refreshTimer) clearInterval(this.refreshTimer)
      this.refreshTimer = null
    }
  }
}
</script>

<style lang="scss" scoped>
.network-status-container {
  padding: 20px 24px;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 0 14px 0;

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #0f172a;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .section-badge-real {
    font-size: 11px;
    font-weight: 600;
    color: #059669;
    background: #d1fae5;
    padding: 1px 8px;
    border-radius: 4px;
    letter-spacing: 0.3px;
  }
}

.asset-cards-row {
  margin-bottom: 8px;

  .real-asset-card {
    padding: 16px 20px;
    margin-bottom: 16px;
    border-radius: 10px;

    .asset-card-inner {
      display: flex;
      align-items: center;
      gap: 16px;

      .asset-icon {
        width: 48px;
        height: 48px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        flex-shrink: 0;

        &.icon-network {
          background: #e0f2fe;
          color: #0284c7;
        }

        &.icon-server {
          background: #f0fdf4;
          color: #10b981;
        }

        &.icon-node {
          background: #ede9fe;
          color: #7c3aed;
        }

        &.icon-health {
          background: #fef3c7;
          color: #d97706;
        }
      }

      .asset-info {
        flex: 1;

        .asset-label {
          font-size: 13px;
          color: #64748b;
          margin-bottom: 2px;
        }

        .asset-value {
          font-size: 22px;
          font-weight: 700;
          color: #0f172a;
          line-height: 1.2;

          .unit {
            font-size: 13px;
            font-weight: normal;
            color: #64748b;
            margin-left: 2px;
          }
        }

        .asset-sub {
          margin-top: 4px;
        }

        .rate-status-tip {
          font-size: 11px;
          color: #059669;
          font-weight: 500;
        }
      }
    }
  }
}

/* 监控卡片 */
.network-card {
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

.card-content {
  display: flex;
  flex-direction: column;
  height: calc(100% - 30px);
  justify-content: space-between;
}

.online-rate {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
}

.network-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;

  .stat-item {
    text-align: center;

    .stat-label {
      display: block;
      font-size: 12px;
      color: #64748b;
      margin-bottom: 4px;
    }

    .stat-value {
      font-size: 18px;
      font-weight: bold;
      color: #0f172a;

      &.online {
        color: #10b981;
      }
    }
  }
}

.response-time, .health-score, .recovery-time {
  text-align: center;
  padding: 10px 0;

  .time-value, .score-value {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 4px;

    &.primary { color: #0284c7; }
    &.warning { color: #f59e0b; }
    &.success-text { color: #10b981; }
    &.warning-text { color: #f59e0b; }
    &.danger-text { color: #ef4444; }

    .unit {
      font-size: 14px;
      font-weight: normal;
      color: #64748b;
      margin-left: 2px;
    }
  }

  .time-desc, .score-desc {
    font-size: 12px;
    color: #64748b;
  }
}

.health-status {
  text-align: center;
  margin-top: 8px;
}

.recovery-progress {
  padding: 10px 15px 0;

  .recovery-footer {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: #64748b;
    margin-top: 6px;
  }
}

.network-chart-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title-with-badge {
      display: flex;
      align-items: center;

      .card-title {
        font-size: 15px;
        font-weight: 600;
        color: #0f172a;
      }
    }
  }
}
</style>

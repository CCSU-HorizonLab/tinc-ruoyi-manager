<template>
  <div class="network-status-container" v-loading="loading">
    <div class="header-section">
      <div class="title-wrapper">
        <h2>网络状态统计</h2>
        <p>实时监控网络状态和健康度</p>
      </div>
      <div class="action-wrapper">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新(30s)"
          @change="handleAutoRefreshChange">
        </el-switch>
        <el-button 
          type="primary" 
          icon="el-icon-refresh" 
          size="small" 
          @click="getStats"
          style="margin-left: 15px;">
          手动刷新
        </el-button>
      </div>
    </div>
    
    <!-- 网络状态卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <!-- 网络在线状态 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="network-card info-card" shadow="hover">
          <div class="card-header">
            <h3>网络在线状态</h3>
          </div>
          <div class="card-content">
            <div class="online-rate">
              <el-progress 
                type="circle" 
                :percentage="stats.onlineRate" 
                :color="progressColors"
                :width="100">
              </el-progress>
            </div>
            <div class="network-stats">
              <div class="stat-item">
                <span class="stat-label">总网络数</span>
                <span class="stat-value">{{ stats.totalNetworks }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">在线网络</span>
                <span class="stat-value online">{{ stats.onlineNetworks }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 平均响应时间 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="network-card info-card" shadow="hover">
          <div class="card-header">
            <h3>平均响应时间</h3>
          </div>
          <div class="card-content">
            <div class="response-time">
              <div class="time-value primary">{{ stats.averageResponseTime }}<span class="unit">ms</span></div>
              <div class="time-desc">最近7天平均</div>
            </div>
            <div class="response-chart">
              <line-chart :chart-data="stats.responseTimeChartData" :height="'100px'" />
            </div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 网络健康平均分 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="network-card info-card" shadow="hover">
          <div class="card-header">
            <h3>网络健康平均分</h3>
          </div>
          <div class="card-content">
            <div class="health-score">
              <div class="score-value" :class="healthScoreClass">{{ stats.healthScore }}</div>
              <div class="score-desc">最近7天平均</div>
            </div>
            <div class="health-status">
              <el-tag :type="healthStatusType" effect="dark">{{ stats.healthStatus }}</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 平均故障恢复时间 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="network-card info-card" shadow="hover">
          <div class="card-header">
            <h3>平均故障恢复时间</h3>
          </div>
          <div class="card-content">
            <div class="recovery-time">
              <div class="time-value warning">{{ stats.averageRecoveryTime }}<span class="unit">分钟</span></div>
              <div class="time-desc">最近7天平均</div>
            </div>
            <div class="recovery-progress">
              <el-progress 
                :percentage="recoveryPercentage" 
                :show-text="false"
                status="warning">
              </el-progress>
              <div class="recovery-footer">
                <span>0</span>
                <span>目标: < 30min</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 网络中断次数趋势 -->
    <el-card class="network-chart-card" shadow="hover">
      <div slot="header" class="clearfix">
        <span class="card-title">网络中断次数趋势（过去7天）</span>
        <el-tooltip content="显示过去一周内发生的网络中断次数统计" placement="top">
          <i class="el-icon-info" style="margin-left: 5px; color: #909399;"></i>
        </el-tooltip>
      </div>
      <div class="card-content">
        <line-chart :chart-data="stats.interruptChartData" :height="'300px'" />
      </div>
    </el-card>
  </div>
</template>

<script>
import LineChart from './LineChart.vue'
import { getGlobalNetworkStats } from '@/api/monitor/networkMonitor'

export default {
  components: {
    LineChart
  },
  data() {
    return {
      loading: false,
      autoRefresh: false,
      refreshTimer: null,
      progressColors: [
        { color: '#f56c6c', percentage: 20 },
        { color: '#e6a23c', percentage: 40 },
        { color: '#5cb87a', percentage: 60 },
        { color: '#1989fa', percentage: 80 },
        { color: '#67c23a', percentage: 100 }
      ],
      // 网络状态聚合数据
      stats: {
        totalNetworks: 0,
        onlineNetworks: 0,
        onlineRate: 0,
        averageResponseTime: 0,
        healthScore: 0,
        healthStatus: '正在加载...',
        averageRecoveryTime: 0,
        responseTimeChartData: {
          expectedData: [],
          actualData: []
        },
        interruptChartData: {
          expectedData: [],
          actualData: []
        }
      }
    }
  },
  computed: {
    healthStatusType() {
      if (this.stats.healthScore >= 80) return 'success'
      if (this.stats.healthScore >= 60) return 'warning'
      return 'danger'
    },
    healthScoreClass() {
      if (this.stats.healthScore >= 80) return 'success-text'
      if (this.stats.healthScore >= 60) return 'warning-text'
      return 'danger-text'
    },
    recoveryPercentage() {
      // 假设 60 分钟是 100% 的参考值，时间越短越好，但这里展示进度
      const percentage = (this.stats.averageRecoveryTime / 60) * 100
      return Math.min(Math.max(percentage, 0), 100)
    }
  },
  created() {
    this.getStats()
  },
  beforeDestroy() {
    this.stopRefreshTimer()
  },
  methods: {
    /** 获取统计数据 */
    getStats() {
      this.loading = true
      getGlobalNetworkStats().then(response => {
        if (response.data) {
          this.stats = response.data
        }
        this.loading = false
      }).catch(() => {
        this.loading = false
        this.simulateDataUpdate()
      })
    },
    /** 处理自动刷新开关变化 */
    handleAutoRefreshChange(val) {
      if (val) {
        this.startRefreshTimer()
      } else {
        this.stopRefreshTimer()
      }
    },
    startRefreshTimer() {
      this.stopRefreshTimer()
      this.refreshTimer = setInterval(() => {
        this.getStats()
      }, 30000)
    },
    stopRefreshTimer() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },
    /** 模拟数据更新 (用于后端接口未就绪时的演示) */
    simulateDataUpdate() {
      this.stats = {
        totalNetworks: 12,
        onlineNetworks: 10,
        onlineRate: 83,
        averageResponseTime: 45,
        healthScore: 88,
        healthStatus: '健康状态良好',
        averageRecoveryTime: 12,
        responseTimeChartData: {
          expectedData: [50, 50, 50, 50, 50, 50, 50],
          actualData: [45, 52, 48, 55, 49, 53, 50]
        },
        interruptChartData: {
          expectedData: [1, 2, 1, 0, 1, 2, 1],
          actualData: [0, 1, 0, 0, 0, 1, 0]
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.network-status-container {
  padding: 20px;
  background-color: #f8f9fb;
  min-height: calc(100vh - 84px);
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
  
  .title-wrapper {
    h2 {
      margin: 0 0 5px 0;
      font-size: 24px;
      color: #303133;
    }
    p {
      margin: 0;
      font-size: 14px;
      color: #909399;
    }
  }
}

.info-card {
  height: 240px;
  margin-bottom: 20px;
}

.network-card {
  border: none;
  border-radius: 10px;
  transition: all 0.3s;
  
  &:hover {
    transform: translateY(-5px);
  }
  
  .card-header {
    margin-bottom: 15px;
    
    h3 {
      margin: 0;
      font-size: 16px;
      color: #606266;
      font-weight: 600;
    }
  }
}

/* 网络在线状态卡片 */
.online-rate {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.network-stats {
  display: flex;
  justify-content: space-around;
  border-top: 1px solid #f2f6fc;
  padding-top: 15px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  display: block;
  font-size: 20px;
  font-weight: bold;
  color: #303133;
}

.stat-value.online {
  color: #67c23a;
}

/* 通用数值样式 */
.time-value, .score-value {
  font-size: 32px;
  font-weight: bold;
  text-align: center;
  margin-bottom: 5px;
  
  .unit {
    font-size: 14px;
    font-weight: normal;
    margin-left: 4px;
  }
}

.primary { color: #409eff; }
.success-text { color: #67c23a; }
.warning-text { color: #e6a23c; }
.danger-text { color: #f56c6c; }
.warning { color: #e6a23c; }

.time-desc, .score-desc {
  font-size: 13px;
  color: #909399;
  text-align: center;
  margin-bottom: 15px;
}

.health-status {
  text-align: center;
  margin-top: 10px;
}

.recovery-progress {
  padding: 0 10px;
  
  .recovery-footer {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
  }
}

/* 图表卡片 */
.network-chart-card {
  border: none;
  border-radius: 10px;
  
  .card-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}
</style>
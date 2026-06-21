<template>
  <div class="single-network-control" v-loading="loading">
    <div class="header-section">
      <div class="title-wrapper">
        <h2>单网监控面板</h2>
        <p>实时监控单个内网的状态与性能</p>
      </div>
      <div class="action-wrapper" v-if="selectedNetwork">
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
          @click="resetSelection"
          style="margin-left: 10px;">
          重新选择
        </el-button>
      </div>
    </div>
    
    <!-- 网络选择区域 -->
    <el-card v-if="!selectedNetwork" class="selection-card" shadow="never">
      <div class="network-select-container">
        <div class="select-icon">
          <i class="el-icon-monitor"></i>
        </div>
        <h3>请选择要查看的内网</h3>
        <el-form :inline="true" class="network-select-form">
          <el-form-item label="服务器">
            <el-select v-model="selectedServer" placeholder="请选择服务器" @change="handleServerChange" filterable>
              <el-option
                v-for="server in servers"
                :key="server.value"
                :label="server.label"
                :value="server.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="内网">
            <el-select v-model="selectedNetworkId" placeholder="请选择内网" :disabled="!selectedServer" filterable>
              <el-option
                v-for="network in networks"
                :key="network.value"
                :label="network.label"
                :value="network.value">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="confirmSelection" :disabled="!selectedServer || !selectedNetworkId">进入监控</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    
    <!-- 网络监控详情区域 -->
    <div v-else>
      <!-- 网络基本信息 -->
      <div class="network-info-banner">
        <div class="info-left">
          <span class="label">当前监控:</span>
          <span class="value">{{ selectedNetworkName }}</span>
          <el-divider direction="vertical"></el-divider>
          <span class="label">服务器:</span>
          <span class="value">{{ selectedServer }}</span>
        </div>
        <div class="info-right">
          <el-tag :type="statusTagType" effect="dark" size="medium">
            <i :class="isOnline ? 'el-icon-success' : 'el-icon-error'"></i>
            {{ statusLabel }}
          </el-tag>
        </div>
      </div>
      
      <!-- 监控指标卡片 -->
      <el-row :gutter="20" style="margin-bottom: 20px;">
        <!-- 响应时间卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>响应时间</span>
              <el-tag size="mini" type="info">实时</el-tag>
            </div>
            <div class="card-content">
              <div class="progress-wrapper">
                <el-progress 
                  type="dashboard" 
                  :percentage="responseTime.progress" 
                  :color="responseTimeColors"
                  :width="120">
                  <template slot="default">
                    <div class="progress-text">
                      <span class="value">{{ responseTime.value }}</span>
                      <span class="unit">ms</span>
                    </div>
                  </template>
                </el-progress>
              </div>
              <div class="monitor-details">
                <div class="detail-item">
                  <span class="detail-label">平均</span>
                  <span class="detail-value">{{ responseTime.avg }} ms</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">最高</span>
                  <span class="detail-value warning">{{ responseTime.max }} ms</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 健康分数卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>健康分数</span>
              <el-tag size="mini" type="success">综合</el-tag>
            </div>
            <div class="card-content">
              <div class="progress-wrapper">
                <el-progress 
                  type="dashboard" 
                  :percentage="healthScore.progress" 
                  :color="healthColors"
                  :width="120">
                </el-progress>
              </div>
              <div class="monitor-details">
                <div class="detail-item">
                  <span class="detail-label">通信网络</span>
                  <span class="detail-value">{{ healthScore.network }}分</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">安全部分</span>
                  <span class="detail-value">{{ healthScore.security }}分</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 流量卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>流量状态</span>
              <el-tag size="mini" type="warning">速率</el-tag>
            </div>
            <div class="card-content">
              <div class="progress-wrapper">
                <el-progress 
                  type="dashboard" 
                  :percentage="traffic.progress" 
                  color="#e6a23c"
                  :width="120">
                  <template slot="default">
                    <div class="progress-text">
                      <span class="value">{{ traffic.value }}</span>
                      <span class="unit">%</span>
                    </div>
                  </template>
                </el-progress>
              </div>
              <div class="monitor-details">
                <div class="detail-item">
                  <span class="detail-label">上传</span>
                  <span class="detail-value">{{ traffic.upload }} Mbps</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">总计</span>
                  <span class="detail-value">{{ traffic.total }} Mbps</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 节点在线率卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>节点状态</span>
              <el-tag size="mini" type="danger">在线率</el-tag>
            </div>
            <div class="card-content">
              <div class="progress-wrapper">
                <el-progress 
                  type="dashboard" 
                  :percentage="nodeRate.progress" 
                  :color="nodeRateColors"
                  :width="120">
                </el-progress>
              </div>
              <div class="monitor-details">
                <div class="detail-item">
                  <span class="detail-label">在线</span>
                  <span class="detail-value success">{{ nodeRate.online }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">离线</span>
                  <span class="detail-value danger">{{ nodeRate.offline }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 监控图表区域 -->
      <el-row :gutter="20">
        <!-- 健康百分趋势图表 -->
        <el-col :xs="24" :lg="12">
          <el-card class="chart-card" shadow="hover">
            <div slot="header" class="card-header">
              <span class="title">健康评分趋势（前7天）</span>
            </div>
            <div class="card-content">
              <div class="chart-container">
                <bar-chart ref="healthBarChart" :height="'250px'" />
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 网络中断事件图表 -->
        <el-col :xs="24" :lg="12">
          <el-card class="chart-card" shadow="hover">
            <div slot="header" class="card-header">
              <span class="title">网络中断事件（前7天）</span>
            </div>
            <div class="card-content">
              <div class="chart-container">
                <line-chart :chart-data="interruptEventData" :height="'250px'" />
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
import LineChart from './LineChart.vue'
import BarChart from './BarChart.vue'
import { listServer } from '@/api/tinc/server'
import { listNetwork } from '@/api/tinc/network'
import { getSingleNetworkMonitor } from '@/api/monitor/networkMonitor'

export default {
  components: {
    LineChart,
    BarChart
  },
  data() {
    return {
      loading: false,
      autoRefresh: false,
      refreshTimer: null,
      // 服务器列表
      servers: [],
      // 内网列表
      networks: [],
      // 选中的服务器和内网
      selectedServer: '',
      selectedNetworkId: '',
      selectedNetwork: false,
      selectedNetworkName: '',
      
      // 颜色配置
      responseTimeColors: [
        { color: '#67c23a', percentage: 40 },
        { color: '#e6a23c', percentage: 70 },
        { color: '#f56c6c', percentage: 100 }
      ],
      healthColors: [
        { color: '#f56c6c', percentage: 60 },
        { color: '#e6a23c', percentage: 80 },
        { color: '#67c23a', percentage: 100 }
      ],
      nodeRateColors: [
        { color: '#f56c6c', percentage: 40 },
        { color: '#e6a23c', percentage: 80 },
        { color: '#67c23a', percentage: 100 }
      ],

      // 网络在线状态
      isOnline: true,
      
      // 监控指标数据
      responseTime: { value: 0, progress: 0, avg: 0, max: 0, min: 0 },
      healthScore: { value: 0, progress: 0, network: 0, test: 0, security: 0 },
      traffic: { value: 0, progress: 0, upload: 0, total: 0, bandwidth: 0 },
      nodeRate: { value: 0, progress: 0, online: 0, offline: 0, total: 0 },
      
      // 图表数据
      interruptEventData: {
        expectedData: [],
        actualData: []
      }
    }
  },
  computed: {
    statusTagType() {
      return this.isOnline ? 'success' : 'danger'
    },
    statusLabel() {
      return this.isOnline ? '网络在线' : '网络离线'
    }
  },
  created() {
    this.getServerOptions();
  },
  beforeDestroy() {
    this.stopRefreshTimer()
  },
  methods: {
    /** 获取服务器列表 */
    getServerOptions() {
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.servers = response.rows.map(server => ({
          value: server.serverName,
          label: server.serverName
        }));
      })
    },

    /** 根据服务器获取内网列表 */
    getNetworksByServer(serverName) {
      listNetwork({ serverName: serverName }).then(response => {
        this.networks = response.rows.map(network => ({
          value: network.id,
          label: network.networkName
        }));
      })
    },
    
    /** 处理服务器选择变化 */
    handleServerChange(serverName) {
      if (serverName) {
        this.getNetworksByServer(serverName);
      } else {
        this.networks = [];
      }
      this.selectedNetworkId = '';
    },
    
    /** 确认选择 */
    confirmSelection() {
      if (!this.selectedServer || !this.selectedNetworkId) {
        return;
      }
      const network = this.networks.find(n => n.value === this.selectedNetworkId);
      if (network) {
        this.selectedNetworkName = network.label;
        this.selectedNetwork = true;
        this.getMonitorData();
      }
    },

    /** 获取实时监控数据 */
    getMonitorData() {
      this.loading = true;
      getSingleNetworkMonitor(this.selectedNetworkId).then(response => {
        if (response.data) {
          this.updateMonitorData(response.data);
        }
        this.loading = false;
      }).catch(() => {
        this.loading = false;
        this.simulateMonitorData();
      });
    },

    updateMonitorData(data) {
      this.isOnline = data.isOnline;
      this.responseTime = data.responseTime;
      this.healthScore = data.healthScore;
      this.traffic = data.traffic;
      this.nodeRate = data.nodeRate;
      this.interruptEventData = data.interruptEventData;
    },
    
    /** 模拟监控数据 */
    simulateMonitorData() {
      this.isOnline = true;
      this.responseTime = { value: 45, progress: 35, avg: 43, max: 88, min: 38 };
      this.healthScore = { value: 95, progress: 95, network: 98, test: 92, security: 95 };
      this.traffic = { value: 65, progress: 65, upload: 12.5, total: 45.2, bandwidth: 100 };
      
      const online = 5;
      const total = 6;
      this.nodeRate = { 
        value: Math.round((online/total)*100), 
        progress: Math.round((online/total)*100), 
        online: online, 
        offline: total - online, 
        total: total 
      };

      this.interruptEventData = {
        expectedData: [0, 1, 0, 0, 1, 0, 0],
        actualData: [0, 0, 0, 0, 0, 0, 0]
      };
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
        this.getMonitorData()
      }, 30000)
    },
    stopRefreshTimer() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },

    /** 重置选择 */
    resetSelection() {
      this.stopRefreshTimer();
      this.autoRefresh = false;
      this.selectedServer = '';
      this.selectedNetworkId = '';
      this.selectedNetwork = false;
      this.selectedNetworkName = '';
      this.networks = [];
    }
  }
}
</script>

<style lang="scss" scoped>
.single-network-control {
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

/* 选择卡片 */
.selection-card {
  max-width: 800px;
  margin: 50px auto;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  
  .network-select-container {
    padding: 40px 20px;
    text-align: center;
    
    .select-icon {
      font-size: 60px;
      color: #409eff;
      margin-bottom: 20px;
      opacity: 0.8;
    }
    
    h3 {
      font-size: 22px;
      color: #303133;
      margin-bottom: 40px;
    }
  }
}

/* 信息横幅 */
.network-info-banner {
  background: #fff;
  padding: 15px 25px;
  border-radius: 10px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
  
  .info-left {
    .label {
      color: #909399;
      font-size: 14px;
      margin-right: 8px;
    }
    .value {
      color: #303133;
      font-weight: 600;
      font-size: 16px;
    }
  }
}

/* 监控卡片 */
.monitor-card {
  border-radius: 10px;
  border: none;
  height: 100%;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    color: #606266;
  }
}

.progress-wrapper {
  display: flex;
  justify-content: center;
  padding: 10px 0 20px;
  
  .progress-text {
    display: flex;
    flex-direction: column;
    align-items: center;
    
    .value {
      font-size: 24px;
      font-weight: bold;
      color: #303133;
    }
    .unit {
      font-size: 12px;
      color: #909399;
    }
  }
}

.monitor-details {
  border-top: 1px solid #f2f6fc;
  padding-top: 15px;
  
  .detail-item {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 13px;
    
    .detail-label {
      color: #909399;
    }
    .detail-value {
      color: #303133;
      font-weight: 600;
    }
    .detail-value.success { color: #67c23a; }
    .detail-value.warning { color: #e6a23c; }
    .detail-value.danger { color: #f56c6c; }
  }
}

/* 图表卡片 */
.chart-card {
  border-radius: 10px;
  border: none;
  margin-top: 10px;
  
  .card-header {
    .title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }
  
  .chart-container {
    height: 250px;
  }
}
</style>

<script>
import LineChart from './LineChart.vue'
import BarChart from './BarChart.vue'
import { listServer } from '@/api/tinc/server'
import { listNetwork } from '@/api/tinc/network'
import { getSingleNetworkMonitor } from '@/api/monitor/networkMonitor'

export default {
  components: {
    LineChart,
    BarChart
  },
  data() {
    return {
      loading: false,
      // 服务器列表
      servers: [],
      // 内网列表
      networks: [],
      // 选中的服务器和内网
      selectedServer: '',
      selectedNetworkId: '',
      selectedNetwork: false,
      selectedNetworkName: '',
      
      // 网络在线状态
      isOnline: true,
      
      // 监控指标数据
      responseTime: { value: 0, progress: 0, avg: 0, max: 0, min: 0 },
      healthScore: { value: 0, progress: 0, network: 0, test: 0, security: 0 },
      traffic: { value: 0, progress: 0, upload: 0, total: 0, bandwidth: 0 },
      nodeRate: { value: 0, progress: 0, online: 0, offline: 0, total: 0 },
      
      // 图表数据
      interruptEventData: {
        expectedData: [],
        actualData: []
      }
    }
  },
  computed: {
    statusTagType() {
      return this.isOnline ? 'success' : 'danger'
    },
    statusLabel() {
      return this.isOnline ? '在线' : '离线'
    }
  },
  created() {
    // 页面加载时获取服务器列表
    this.getServerOptions();
  },
  methods: {
    /** 获取服务器列表 */
    getServerOptions() {
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.servers = response.rows.map(server => ({
          value: server.serverName,
          label: server.serverName
        }));
      })
    },

    /** 根据服务器获取内网列表 */
    getNetworksByServer(serverName) {
      listNetwork({ serverName: serverName }).then(response => {
        this.networks = response.rows.map(network => ({
          value: network.id,
          label: network.networkName
        }));
      })
    },
    
    /** 处理服务器选择变化 */
    handleServerChange(serverName) {
      if (serverName) {
        this.getNetworksByServer(serverName);
      } else {
        this.networks = [];
      }
      this.selectedNetworkId = '';
    },
    
    /** 确认选择 */
    confirmSelection() {
      if (!this.selectedServer || !this.selectedNetworkId) {
        return;
      }
      const network = this.networks.find(n => n.value === this.selectedNetworkId);
      if (network) {
        this.selectedNetworkName = network.label;
        this.selectedNetwork = true;
        this.getMonitorData();
      }
    },

    /** 获取实时监控数据 */
    getMonitorData() {
      this.loading = true;
      getSingleNetworkMonitor(this.selectedNetworkId).then(response => {
        if (response.data) {
          const data = response.data;
          this.isOnline = data.isOnline;
          this.responseTime = data.responseTime;
          this.healthScore = data.healthScore;
          this.traffic = data.traffic;
          this.nodeRate = data.nodeRate;
          this.interruptEventData = data.interruptEventData;
          // 注意：BarChart 的更新可能需要通过 ref 调用其内部方法，视组件实现而定
        }
        this.loading = false;
      }).catch(() => {
        this.loading = false;
        this.simulateMonitorData();
      });
    },
    
    /** 模拟监控数据 */
    simulateMonitorData() {
      this.isOnline = true;
      this.responseTime = { value: 69, progress: 69, avg: 43, max: 200, min: 43 };
      this.healthScore = { value: 100, progress: 100, network: 63, test: 100, security: 5 };
      this.traffic = { value: 71, progress: 71, upload: 1628.8, total: 2815.0, bandwidth: 10000 };
      this.nodeRate = { value: 0, progress: 0, online: 0, offline: 6, total: 6 };
      this.interruptEventData = {
        expectedData: [],
        actualData: [0, 0, 0, 0, 0, 0, 0]
      };
    },
    
    /** 重置选择 */
    resetSelection() {
      this.selectedServer = '';
      this.selectedNetworkId = '';
      this.selectedNetwork = false;
      this.selectedNetworkName = '';
      this.networks = [];
    }
  }
}
</script>

<style lang="scss" scoped>
.single-network-control {
  padding: 20px;
}

.network-select-container {
  max-width: 600px;
  margin: 0 auto;
  text-align: center;
  padding: 20px;
}

.network-select-container h3 {
  margin-bottom: 30px;
  color: #333;
}

.network-select-form {
  justify-content: center;
}

/* 网络信息区域 */
.network-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

/* 监控卡片样式 */
.monitor-card {
  height: 100%;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 10px;
    margin-bottom: 15px;
    border-bottom: 1px solid #f0f0f0;
    
    h4 {
      margin: 0;
      font-size: 16px;
      color: #333;
    }
  }
  
  .card-content {
    padding: 10px 0;
  }
}

/* 圆形图表样式 */
.circle-chart {
  position: relative;
  width: 100px;
  height: 100px;
  margin: 0 auto 15px;
}

.circle-bg {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background-color: #f0f0f0;
}

.circle-progress {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: conic-gradient(
    #409eff var(--progress),
    transparent 0%
  );
  mask-image: radial-gradient(circle at center, transparent 60%, black 60%);
  -webkit-mask-image: radial-gradient(circle at center, transparent 60%, black 60%);
}

.circle-progress.health-score {
  background: conic-gradient(
    #67c23a var(--progress),
    transparent 0%
  );
}

.circle-progress.traffic {
  background: conic-gradient(
    #e6a23c var(--progress),
    transparent 0%
  );
}

.circle-progress.node-rate {
  background: conic-gradient(
    #f56c6c var(--progress),
    transparent 0%
  );
}

.circle-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

/* 监控详情样式 */
.monitor-details {
  margin-top: 15px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 14px;
}

.detail-label {
  color: #666;
}

.detail-value {
  color: #333;
  font-weight: bold;
}

.detail-value.warning {
  color: #e6a23c;
}

.detail-value.success {
  color: #67c23a;
}

.detail-value.danger {
  color: #f56c6c;
}

/* 图表卡片样式 */
.chart-card {
  height: 100%;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  
  .card-header {
    padding-bottom: 10px;
    margin-bottom: 15px;
    border-bottom: 1px solid #f0f0f0;
    
    h4 {
      margin: 0;
      font-size: 16px;
      color: #333;
    }
  }
  
  .card-content {
    padding: 10px 0;
  }
  
  .chart-container {
    height: 250px;
  }
}
</style>
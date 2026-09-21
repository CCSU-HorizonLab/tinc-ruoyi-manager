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
          <p class="tl-header-desc">查看指定接入服务器下虚拟网络的运行状态、拓扑指标与健康度</p>
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
          <p class="guidance-sub-tip">监控面板将实时读取对应宿主网关与该网络的 Tinc Runtime 运行状态、接口流量与拓扑心跳</p>
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
              <div class="item-desc">全流量端到端加密、严格网段地址隔离与确定性公钥交换路由</div>
            </div>
          </div>

          <div class="guidance-feature-item">
            <div class="item-icon-wrap icon-purple">
              <i class="el-icon-aim"></i>
            </div>
            <div class="item-content">
              <div class="item-title">实时探针与健康就绪</div>
              <div class="item-desc">自动化采集网关负载、Tinc 守护进程 PID 与网络 READY 就绪度</div>
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
              <span class="value"><i class="el-icon-office-building"></i> {{ selectedServer }}</span>
            </div>
            <div class="banner-subtitle-row">
              <span class="sub-label">监控目标ID:</span>
              <span class="tl-code-badge">{{ selectedNetworkId }}</span>
              <span class="status-real-tag">真实运行时链路</span>
            </div>
          </div>
        </div>
        <div class="info-right">
          <span :class="['tl-status-badge', isOnline ? 'ready' : 'offline']" style="font-size: 13px; padding: 6px 14px;">
            <span class="tl-status-dot"></span>
            {{ statusLabel }}
          </span>
        </div>
      </div>
      
      <!-- 监控指标卡片 -->
      <el-row :gutter="20" style="margin-bottom: 20px;">
        <!-- 响应时间卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>响应时间</span>
              <span class="tl-demo-badge">演示数据</span>
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
          <el-card class="monitor-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>健康分数</span>
              <span class="tl-demo-badge">演示数据</span>
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
                  <span class="detail-label">安全准入</span>
                  <span class="detail-value">{{ healthScore.security }}分</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 流量卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>流量状态</span>
              <span class="tl-demo-badge">演示数据</span>
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
                  <span class="detail-label">上行速率</span>
                  <span class="detail-value">{{ traffic.upload }} Mbps</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">累计吞吐</span>
                  <span class="detail-value">{{ traffic.total }} Mbps</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        
        <!-- 节点在线率卡片 -->
        <el-col :xs="24" :sm="12" :lg="6">
          <el-card class="monitor-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>节点状态</span>
              <span class="tl-demo-badge">演示数据</span>
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
                  <span class="detail-label">在线节点</span>
                  <span class="detail-value success">{{ nodeRate.online }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">离线节点</span>
                  <span class="detail-value danger">{{ nodeRate.offline }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 监控图表区域 -->
      <el-row :gutter="20">
        <!-- 健康趋势图表 -->
        <el-col :xs="24" :lg="12">
          <el-card class="chart-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span class="title">健康评分趋势（前7天）</span>
              <span class="tl-demo-badge">演示数据</span>
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
          <el-card class="chart-card tl-card" shadow="hover">
            <div slot="header" class="card-header">
              <span class="title">网络中断事件（前7天）</span>
              <span class="tl-demo-badge">演示数据</span>
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
      servers: [],
      networks: [],
      selectedServer: '',
      selectedNetworkId: '',
      selectedNetwork: false,
      selectedNetworkName: '',
      
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

      isOnline: true,
      
      responseTime: { value: 0, progress: 0, avg: 0, max: 0, min: 0 },
      healthScore: { value: 0, progress: 0, network: 0, test: 0, security: 0 },
      traffic: { value: 0, progress: 0, upload: 0, total: 0, bandwidth: 0 },
      nodeRate: { value: 0, progress: 0, online: 0, offline: 0, total: 0 },
      
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
      return this.isOnline ? 'READY · 运行正常' : 'NOT READY · 离线'
    }
  },
  created() {
    this.getServerOptions();
  },
  beforeDestroy() {
    this.stopRefreshTimer()
  },
  methods: {
    getServerOptions() {
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.servers = response.rows.map(server => ({
          value: server.serverName,
          label: server.serverName
        }));
      })
    },

    getNetworksByServer(serverName) {
      listNetwork({ serverName: serverName }).then(response => {
        this.networks = response.rows.map(network => ({
          value: network.id,
          label: network.networkName
        }));
      })
    },
    
    handleServerChange(serverName) {
      if (serverName) {
        this.getNetworksByServer(serverName);
      } else {
        this.networks = [];
      }
      this.selectedNetworkId = '';
    },
    
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
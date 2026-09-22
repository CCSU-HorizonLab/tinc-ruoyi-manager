<template>
  <div class="aiops-container-root">
    <!-- 浮动唤醒按钮 -->
    <div
      v-show="!drawerVisible"
      class="aiops-fab"
      @click="openDrawer"
      title="唤醒 TincLink AIOps 智能运维专家"
    >
      <span class="aiops-fab-icon-wrap">
        <i class="el-icon-cpu"></i>
      </span>
      <span class="aiops-fab-text">
        <span class="aiops-fab-short">AIOps</span>
        <span class="aiops-fab-full">AIOps 智能诊断</span>
      </span>
    </div>

    <!-- 右侧排障抽屉 -->
    <el-drawer
      :visible.sync="drawerVisible"
      direction="rtl"
      custom-class="aiops-drawer"
      :before-close="handleBeforeClose"
      :destroy-on-close="false"
      size="480px"
    >
      <!-- 自定义抽屉顶部 -->
      <div slot="title" class="aiops-header">
        <div class="aiops-header-main">
          <div class="aiops-header-icon">
            <i class="el-icon-data-analysis"></i>
          </div>
          <div class="aiops-header-info">
            <div class="aiops-title-row">
              <span class="aiops-title">TincLink AIOps 智能运维专家</span>
              <el-tag
                size="mini"
                type="info"
                effect="plain"
                class="aiops-service-badge"
              >
                <i class="el-icon-cpu"></i> 专家规则诊断引擎
              </el-tag>
            </div>
            <div class="aiops-subtitle">网络故障诊断与排障辅助</div>
          </div>
        </div>
      </div>

      <!-- 抽屉主体 -->
      <div class="aiops-body">
        <!-- 诊断上下文折叠面板 (Stable-ID 拓扑关联) -->
        <div class="aiops-section context-section">
          <el-collapse v-model="activeCollapse">
            <el-collapse-item name="context">
              <template slot="title">
                <div class="collapse-title">
                  <i class="el-icon-connection"></i>
                  <span>网络拓扑诊断上下文 (Stable-ID 关联)</span>
                  <el-tag v-if="contextSummaryText" size="mini" type="primary" class="context-tag">
                    {{ contextSummaryText }}
                  </el-tag>
                </div>
              </template>
              <div class="context-form">
                <div class="context-item">
                  <label class="context-label">接入服务器 (Server)</label>
                  <el-select
                    v-model="contextForm.serverId"
                    placeholder="选择服务器 (ID 绑定)"
                    size="small"
                    clearable
                    style="width: 100%;"
                    @change="handleServerChange"
                  >
                    <el-option
                      v-for="item in serverOptions"
                      :key="item.id"
                      :label="item.serverName + ' (ID: ' + item.id + ')'"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.serverName }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">
                        ID: {{ item.id }} | {{ item.agentStatus || 'READY' }}
                      </span>
                    </el-option>
                  </el-select>
                </div>

                <div class="context-item">
                  <label class="context-label">虚拟网络 (Network)</label>
                  <el-select
                    v-model="contextForm.networkId"
                    placeholder="选择虚拟网络 (ID 绑定)"
                    size="small"
                    clearable
                    style="width: 100%;"
                    @change="handleNetworkChange"
                  >
                    <el-option
                      v-for="item in networkOptions"
                      :key="item.id"
                      :label="item.networkName + ' (ID: ' + item.id + ')'"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.networkName }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">
                        ID: {{ item.id }} | {{ item.segment || '' }}
                      </span>
                    </el-option>
                  </el-select>
                </div>

                <div class="context-item">
                  <label class="context-label">目标节点 (Node)</label>
                  <el-select
                    v-model="contextForm.nodeId"
                    placeholder="选择节点 (ID 绑定)"
                    size="small"
                    clearable
                    style="width: 100%;"
                  >
                    <el-option
                      v-for="item in nodeOptions"
                      :key="item.id"
                      :label="item.nodeName + ' (ID: ' + item.id + ')'"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.nodeName }}</span>
                      <span style="float: right; color: #8492a6; font-size: 12px">
                        ID: {{ item.id }}
                      </span>
                    </el-option>
                  </el-select>
                </div>

                <div class="context-security-tip">
                  <i class="el-icon-lock"></i>
                  <span>安全边界：仅传递公开 Stable-ID 与拓扑状态，绝不发送私钥、Token、密码或 Agent Secret。</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 故障现象输入区 -->
        <div class="aiops-section">
          <div class="section-title">
            <span class="required-mark">*</span> 故障现象
            <span class="section-tip">(1~200 字)</span>
          </div>
          <el-input
            v-model="queryForm.issue"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="例如：Windows 节点无法连接 Ubuntu 接入服务器，或 DEV_B 显示 READY 但客户端无法 Ping 通网关"
            class="issue-input"
            clearable
          />
        </div>

        <!-- 错误日志 / 终端输出输入区 -->
        <div class="aiops-section">
          <div class="section-header-row">
            <div class="section-title">
              系统报错日志 / 终端输出
            </div>
            <div class="section-actions">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-document"
                @click="fillSampleLog"
              >
                示例日志
              </el-button>
              <el-button
                v-if="queryForm.logs"
                size="mini"
                type="text"
                icon="el-icon-delete"
                class="danger-text-btn"
                @click="clearLogs"
              >
                清空日志
              </el-button>
            </div>
          </div>
          <el-input
            v-model="queryForm.logs"
            type="textarea"
            :rows="7"
            placeholder="支持粘贴 Tinc log、systemctl status、Windows Agent log、Ping 输出、Runtime error、Management error 等..."
            class="monospace-textarea"
          />

          <!-- 敏感信息脱敏提示 -->
          <div v-if="sanitizationStats.count > 0" class="redaction-tip">
            <i class="el-icon-circle-check"></i>
            <span>已自动执行安全脱敏：过滤 {{ sanitizationStats.count }} 处敏感凭据/密钥并置换为 [REDACTED]</span>
          </div>
        </div>

        <!-- 操作按钮组 -->
        <div class="aiops-section action-section">
          <el-button
            type="primary"
            icon="el-icon-search"
            class="diagnose-submit-btn"
            :loading="loading"
            :disabled="!canSubmit"
            @click="handleDiagnose"
          >
            {{ loading ? '正在分析网络状态与日志...' : '开始诊断' }}
          </el-button>

          <el-button
            icon="el-icon-refresh-left"
            :disabled="loading"
            class="reset-btn"
            @click="handleResetAll"
          >
            重置
          </el-button>
        </div>

        <!-- 超时提示 -->
        <div v-if="isSlowResponse" class="slow-response-tip">
          <i class="el-icon-time"></i>
          <span>诊断耗时较长，正在分析拓扑规则，请继续等待或稍后重试...</span>
        </div>

        <!-- 空状态与推荐快捷问题 -->
        <div v-if="!diagnosticResult && !loading" class="empty-quick-container">
          <div class="empty-intro">
            <div class="empty-icon"><i class="el-icon-cpu"></i></div>
            <div class="empty-text">
              描述故障现象并粘贴相关日志，AIOps 将结合当前 TincLink 网络状态提供排障建议。
            </div>
          </div>

          <div class="quick-title">
            <i class="el-icon-magic-stick"></i> 快捷诊断模板
          </div>
          <div class="quick-cards">
            <div
              v-for="tpl in quickTemplates"
              :key="tpl.id"
              class="quick-card"
              @click="applyQuickTemplate(tpl)"
            >
              <div class="quick-card-header">
                <el-tag size="mini" :type="tpl.tagType" effect="light">{{ tpl.tag }}</el-tag>
                <span class="quick-card-title">{{ tpl.title }}</span>
              </div>
              <div class="quick-card-desc">{{ tpl.issue }}</div>
            </div>
          </div>
        </div>

        <!-- 错误状态提示 -->
        <div v-if="errorMessage" class="error-container">
          <el-alert
            title="诊断暂时不可用"
            type="error"
            :description="errorMessage"
            show-icon
            :closable="false"
          />
          <div class="error-actions">
            <el-button size="small" type="primary" plain icon="el-icon-refresh" @click="handleDiagnose">
              重新尝试
            </el-button>
          </div>
        </div>

        <!-- 结构化诊断结果区域 -->
        <div v-if="diagnosticResult" class="result-container">
          <div class="result-header">
            <div class="result-header-title">
              <i class="el-icon-pie-chart"></i> 诊断结论报告
            </div>
            <div class="result-header-tags">
              <el-tag size="mini" :type="diagnosticResult.statusType || 'danger'" effect="dark">
                {{ diagnosticResult.engine === 'RULE_BASED' ? '规则诊断' : '实时分析' }}
              </el-tag>
              <el-tag
                v-if="diagnosticResult.confidence"
                size="mini"
                type="info"
                effect="plain"
                class="confidence-tag"
              >
                置信度: {{ confidenceLabel(diagnosticResult.confidence) }}
              </el-tag>
            </div>
          </div>

          <!-- 1. 诊断结论 -->
          <div class="result-card summary-card">
            <div class="card-label">诊断结论</div>
            <div class="summary-content">
              {{ diagnosticResult.summary }}
            </div>
            <div class="source-subtext">
              依据：{{ diagnosticResult.source }}
            </div>
          </div>

          <!-- 2. 可能原因 -->
          <div v-if="diagnosticResult.possibleCauses && diagnosticResult.possibleCauses.length" class="result-card causes-card">
            <div class="card-label">可能原因分析</div>
            <ul class="causes-list">
              <li v-for="(cause, index) in diagnosticResult.possibleCauses" :key="index" class="cause-item">
                <span class="cause-index">{{ index + 1 }}</span>
                <span class="cause-text">{{ cause }}</span>
              </li>
            </ul>
          </div>

          <!-- 3. 建议检查与命令 -->
          <div v-if="diagnosticResult.checks && diagnosticResult.checks.length" class="result-card checks-card">
            <div class="card-label">建议排障步骤与命令</div>
            <div class="checks-list">
              <div v-for="(check, cIdx) in diagnosticResult.checks" :key="cIdx" class="check-box">
                <div class="check-header">
                  <i class="el-icon-check check-icon"></i>
                  <span class="check-title">{{ check.title }}</span>
                </div>
                <div class="check-desc">{{ check.description }}</div>
                <div v-if="check.command" class="command-block">
                  <div class="command-content">
                    <code>{{ check.command }}</code>
                  </div>
                  <el-tooltip content="复制排障命令" placement="top">
                    <el-button
                      size="mini"
                      type="text"
                      icon="el-icon-copy-document"
                      class="copy-btn"
                      @click="copyCommand(check.command)"
                    >
                      复制
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
            </div>
            <div class="command-security-notice">
              <i class="el-icon-warning-outline"></i>
              <span>安全规范：AIOps 仅建议排障命令，系统严禁远程执行任意 Shell，请由运维人员核实后手动执行。</span>
            </div>
          </div>

          <!-- 4. 风险提示 -->
          <div v-if="diagnosticResult.warnings && diagnosticResult.warnings.length" class="result-card warnings-card">
            <div class="card-label warning-label">
              <i class="el-icon-warning"></i> 风险提示与注意事项
            </div>
            <ul class="warnings-list">
              <li v-for="(w, wIdx) in diagnosticResult.warnings" :key="wIdx" class="warning-item">
                {{ w }}
              </li>
            </ul>
          </div>

          <!-- 5. 诊断依据 -->
          <div v-if="diagnosticResult.evidence && diagnosticResult.evidence.length" class="result-card evidence-card">
            <div class="card-label">网络拓扑诊断依据</div>
            <div class="evidence-tags">
              <el-tag
                v-for="(ev, eIdx) in diagnosticResult.evidence"
                :key="eIdx"
                size="small"
                type="info"
                class="evidence-tag"
              >
                {{ ev }}
              </el-tag>
            </div>
          </div>

          <!-- 6. 交互追问区域 -->
          <div class="follow-up-section">
            <div class="follow-up-title">
              <i class="el-icon-chat-dot-round"></i> 进一步排障追问
            </div>
            <div class="follow-up-input-row">
              <el-input
                v-model="followUpQuery"
                size="small"
                placeholder="例如：我已经确认 655 端口监听正常，下一步呢？"
                @keyup.enter.native="handleFollowUp"
              />
              <el-button
                size="small"
                type="primary"
                :disabled="!followUpQuery.trim()"
                @click="handleFollowUp"
              >
                追问
              </el-button>
            </div>

            <!-- 追问回复展示 -->
            <div v-if="followUpResponse" class="follow-up-reply-card">
              <div class="follow-up-reply-title">
                <i class="el-icon-s-opportunity"></i> {{ followUpResponse.title }}
              </div>
              <div class="follow-up-reply-answer">
                {{ followUpResponse.answer }}
              </div>
              <div v-if="followUpResponse.suggestedCommand" class="command-block">
                <div class="command-content">
                  <code>{{ followUpResponse.suggestedCommand }}</code>
                </div>
                <el-button
                  size="mini"
                  type="text"
                  icon="el-icon-copy-document"
                  class="copy-btn"
                  @click="copyCommand(followUpResponse.suggestedCommand)"
                >
                  复制
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listServer } from '@/api/tinc/server'
import { listNetwork } from '@/api/tinc/network'
import { listNode } from '@/api/tinc/node'
import {
  diagnoseIssue,
  ruleBasedDiagnose,
  getAiOpsQuickTemplates,
  followUpDiagnose
} from '@/api/tinc/aiops'
import { sanitizeDiagnosticInput } from '@/utils/aiopsSanitizer'

export default {
  name: 'AiAssistant',
  data() {
    return {
      drawerVisible: false,
      loading: false,
      isSlowResponse: false,
      slowTimer: null,
      activeCollapse: [],
      errorMessage: '',
      serverOptions: [],
      networkOptions: [],
      nodeOptions: [],
      quickTemplates: getAiOpsQuickTemplates(),
      queryForm: {
        issue: '',
        logs: ''
      },
      contextForm: {
        serverId: null,
        networkId: null,
        nodeId: null
      },
      sanitizationStats: {
        count: 0,
        detectedTypes: []
      },
      diagnosticResult: null,
      followUpQuery: '',
      followUpResponse: null
    }
  },
  computed: {
    canSubmit() {
      return !!(this.queryForm.issue.trim() || this.queryForm.logs.trim())
    },
    contextSummaryText() {
      const parts = []
      if (this.contextForm.serverId) parts.push(`Server #${this.contextForm.serverId}`)
      if (this.contextForm.networkId) parts.push(`Net #${this.contextForm.networkId}`)
      if (this.contextForm.nodeId) parts.push(`Node #${this.contextForm.nodeId}`)
      return parts.join(' | ')
    }
  },
  watch: {
    'queryForm.logs'(newVal) {
      if (!newVal) {
        this.sanitizationStats = { count: 0, detectedTypes: [] }
      }
    }
  },
  mounted() {
    this.fetchTopologyOptions()
  },
  methods: {
    openDrawer() {
      this.drawerVisible = true
      this.fetchTopologyOptions()
    },
    handleBeforeClose(done) {
      this.drawerVisible = false
      if (typeof done === 'function') {
        done()
      }
    },
    fetchTopologyOptions() {
      listServer({ pageNum: 1, pageSize: 100 }).then(res => {
        this.serverOptions = res.rows || []
      }).catch(() => {
        // 容错处理
      })

      listNetwork({ pageNum: 1, pageSize: 100 }).then(res => {
        this.networkOptions = res.rows || []
      }).catch(() => {
        // 容错处理
      })

      listNode({ pageNum: 1, pageSize: 100 }).then(res => {
        this.nodeOptions = res.rows || []
      }).catch(() => {
        // 容错处理
      })
    },
    handleServerChange(serverId) {
      if (serverId) {
        listNetwork({ serverId }).then(res => {
          this.networkOptions = res.rows || []
        }).catch(() => {})
      }
    },
    handleNetworkChange(networkId) {
      if (networkId) {
        listNode({ networkId }).then(res => {
          this.nodeOptions = res.rows || []
        }).catch(() => {})
      }
    },
    clearLogs() {
      this.queryForm.logs = ''
      this.sanitizationStats = { count: 0, detectedTypes: [] }
      this.$message.info('日志已清空')
    },
    fillSampleLog() {
      const sample = `2026-09-22 20:18:32 [tinc.DEV_B] Cannot open device /dev/net/tun: No such file or directory
2026-09-22 20:18:32 [tinc.DEV_B] Terminating
2026-09-22 20:18:35 [Systemd] Failed to start tinc@DEV_B.service: Unit tinc@DEV_B.service entered failed state.
2026-09-22 20:18:36 [Tinc-Agent] Warning: TCP/UDP 655 port not listening.`
      this.queryForm.logs = sample
      if (!this.queryForm.issue) {
        this.queryForm.issue = 'DEV_B 虚拟网络启动失败，TUN 设备无法打开'
      }
      this.$message.success('已填充典型故障报错日志')
    },
    applyQuickTemplate(tpl) {
      this.queryForm.issue = tpl.issue
      this.queryForm.logs = tpl.logs
      this.sanitizationStats = { count: 0, detectedTypes: [] }
      this.diagnosticResult = null
      this.errorMessage = ''
      this.followUpResponse = null
      this.$message.success(`已载入模板：${tpl.title}`)
    },
    handleResetAll() {
      this.queryForm.issue = ''
      this.queryForm.logs = ''
      this.sanitizationStats = { count: 0, detectedTypes: [] }
      this.diagnosticResult = null
      this.errorMessage = ''
      this.followUpQuery = ''
      this.followUpResponse = null
      this.$message.info('输入与诊断结果已重置')
    },
    handleDiagnose() {
      if (!this.canSubmit) {
        this.$message.warning('请提供故障描述或报错日志！')
        return
      }

      this.loading = true
      this.errorMessage = ''
      this.isSlowResponse = false
      this.diagnosticResult = null
      this.followUpResponse = null

      // 设置慢响应计时器
      this.slowTimer = setTimeout(() => {
        if (this.loading) {
          this.isSlowResponse = true
        }
      }, 7000)

      // 1. 客户端敏感信息安全脱敏
      const issueSanitized = sanitizeDiagnosticInput(this.queryForm.issue)
      const logsSanitized = sanitizeDiagnosticInput(this.queryForm.logs)

      const totalRedacted = issueSanitized.redactedCount + logsSanitized.redactedCount
      const combinedTypes = Array.from(new Set([...issueSanitized.detectedTypes, ...logsSanitized.detectedTypes]))

      this.sanitizationStats = {
        count: totalRedacted,
        detectedTypes: combinedTypes
      }

      // 如果存在脱敏，更新文本框显示
      if (totalRedacted > 0) {
        this.queryForm.issue = issueSanitized.sanitizedText
        this.queryForm.logs = logsSanitized.sanitizedText
        this.$message.info(`检测到敏感信息（${combinedTypes.join(', ')}），已在本地完成安全脱敏替换！`)
      }

      // 2. 收集拓扑上下文 (Stable-ID 契约)
      const selectedServer = this.serverOptions.find(s => s.id === this.contextForm.serverId)
      const selectedNetwork = this.networkOptions.find(n => n.id === this.contextForm.networkId)
      const selectedNode = this.nodeOptions.find(nd => nd.id === this.contextForm.nodeId)

      const contextPayload = {
        serverId: this.contextForm.serverId || undefined,
        networkId: this.contextForm.networkId || undefined,
        nodeId: this.contextForm.nodeId || undefined,
        serverName: selectedServer ? selectedServer.serverName : undefined,
        agentStatus: selectedServer ? selectedServer.agentStatus : undefined,
        networkName: selectedNetwork ? selectedNetwork.networkName : undefined,
        readiness: selectedNetwork ? selectedNetwork.readiness : undefined,
        segment: selectedNetwork ? selectedNetwork.segment : undefined,
        nodeName: selectedNode ? selectedNode.nodeName : undefined
      }

      const requestData = {
        issue: issueSanitized.sanitizedText,
        logs: logsSanitized.sanitizedText,
        context: {
          serverId: contextPayload.serverId,
          networkId: contextPayload.networkId,
          nodeId: contextPayload.nodeId
        }
      }

      // 3. 发起诊断请求 (优先尝试后端契约接口，脱机或未配置时自动优雅降级为专家规则引擎)
      diagnoseIssue(requestData)
        .then(response => {
          this.clearSlowTimer()
          this.loading = false
          if (response && response.data) {
            this.diagnosticResult = response.data
            this.$message.success('诊断分析完成！')
          } else {
            // 降级使用规则引擎
            this.fallbackRuleDiagnose(issueSanitized.sanitizedText, logsSanitized.sanitizedText, contextPayload)
          }
        })
        .catch(err => {
          this.clearSlowTimer()
          this.loading = false
          // 后端未配置或接口尚未开启时，绝不报错白屏，自动启用 TincLink 专家规则诊断引擎
          console.warn('[AIOps] 远程诊断接口不可用，切换至 TincLink 内置规则引擎:', err.message || err)
          this.fallbackRuleDiagnose(issueSanitized.sanitizedText, logsSanitized.sanitizedText, contextPayload)
        })
    },
    fallbackRuleDiagnose(issue, logs, context) {
      const ruleResult = ruleBasedDiagnose(issue, logs, context)
      this.diagnosticResult = ruleResult
      this.$message.success('已基于 TincLink 内置专家知识库完成排障诊断')
    },
    clearSlowTimer() {
      if (this.slowTimer) {
        clearTimeout(this.slowTimer)
        this.slowTimer = null
      }
      this.isSlowResponse = false
    },
    confidenceLabel(conf) {
      if (!conf) return '中'
      const c = String(conf).toUpperCase()
      if (c === 'HIGH' || c === '高') return '高'
      if (c === 'MEDIUM' || c === '中') return '中'
      if (c === 'LOW' || c === '低') return '低'
      return conf
    },
    copyCommand(cmd) {
      if (!cmd) return
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(cmd).then(() => {
          this.$message.success('排障命令已复制到剪贴板')
        }).catch(() => {
          this.fallbackCopy(cmd)
        })
      } else {
        this.fallbackCopy(cmd)
      }
    },
    fallbackCopy(cmd) {
      const input = document.createElement('textarea')
      input.value = cmd
      input.style.position = 'fixed'
      input.style.opacity = '0'
      document.body.appendChild(input)
      input.select()
      try {
        document.execCommand('copy')
        this.$message.success('排障命令已复制到剪贴板')
      } catch (err) {
        this.$message.error('复制失败，请手动选择命令文本')
      }
      document.body.removeChild(input)
    },
    handleFollowUp() {
      if (!this.followUpQuery.trim()) return
      const res = followUpDiagnose(this.diagnosticResult, this.followUpQuery.trim())
      this.followUpResponse = res
      this.$message.info('已获取追问排障指引')
    }
  }
}
</script>

<style scoped>
/* 浮动按钮 */
.aiops-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  background: linear-gradient(135deg, #1890ff 0%, #0077b6 100%);
  color: #ffffff;
  padding: 10px 16px;
  border-radius: 24px;
  box-shadow: 0 4px 14px rgba(24, 144, 255, 0.35);
  cursor: pointer;
  z-index: 1999;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  user-select: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.aiops-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(24, 144, 255, 0.5);
}

.aiops-fab-icon-wrap {
  font-size: 16px;
  display: flex;
  align-items: center;
}

.aiops-fab-text {
  font-size: 13px;
  letter-spacing: 0.5px;
}

.aiops-fab-full {
  display: none;
}

.aiops-fab:hover .aiops-fab-short {
  display: none;
}

.aiops-fab:hover .aiops-fab-full {
  display: inline;
}

/* 抽屉整体 */
::v-deep .aiops-drawer {
  max-width: 100vw;
  box-shadow: -4px 0 24px rgba(15, 23, 42, 0.12);
}

@media screen and (max-width: 768px) {
  ::v-deep .aiops-drawer {
    width: 100% !important;
  }
}

@media screen and (min-width: 769px) and (max-width: 1440px) {
  ::v-deep .aiops-drawer {
    width: 420px !important;
  }
}

@media screen and (min-width: 1441px) {
  ::v-deep .aiops-drawer {
    width: 480px !important;
  }
}

/* 抽屉头部 */
.aiops-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.aiops-header-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.aiops-header-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #e6f7ff;
  color: #1890ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.aiops-header-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.aiops-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.aiops-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.aiops-service-badge {
  font-weight: 500;
  border-radius: 4px;
}

.aiops-subtitle {
  font-size: 12px;
  color: #64748b;
}

/* 抽屉主体滚动区 */
.aiops-body {
  padding: 16px 20px 32px 20px;
  height: calc(100vh - 72px);
  overflow-y: auto;
  box-sizing: border-box;
}

.aiops-body::-webkit-scrollbar {
  width: 6px;
}
.aiops-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

/* 通用 Section */
.aiops-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.required-mark {
  color: #f56c6c;
}

.section-tip {
  font-size: 12px;
  font-weight: normal;
  color: #94a3b8;
}

.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.danger-text-btn {
  color: #f56c6c;
}
.danger-text-btn:hover {
  color: #e63946;
}

/* 上下文面板 */
.context-section ::v-deep .el-collapse {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.context-section ::v-deep .el-collapse-item__header {
  background: #f8fafc;
  padding: 0 14px;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  border-bottom: 1px solid #e2e8f0;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.context-tag {
  margin-left: auto;
  margin-right: 12px;
}

.context-form {
  padding: 12px 14px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.context-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.context-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.context-security-tip {
  margin-top: 4px;
  padding: 8px 10px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 11px;
  color: #475569;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  line-height: 1.4;
}

/* 输入框与等宽日志框 */
.monospace-textarea ::v-deep textarea {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  background-color: #f8fafc;
  border-color: #cbd5e1;
  color: #1e293b;
}

.issue-input ::v-deep textarea {
  font-size: 13px;
  line-height: 1.5;
}

/* 敏感信息脱敏标识 */
.redaction-tip {
  margin-top: 6px;
  padding: 6px 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  font-size: 11px;
  color: #166534;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 操作按钮 */
.action-section {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.diagnose-submit-btn {
  flex: 1;
  height: 40px;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #1890ff 0%, #0077b6 100%);
  border: none;
  border-radius: 6px;
}

.diagnose-submit-btn:hover {
  background: linear-gradient(135deg, #40a9ff 0%, #0096c7 100%);
}

.reset-btn {
  height: 40px;
  border-radius: 6px;
}

/* 慢响应提示 */
.slow-response-tip {
  padding: 8px 12px;
  margin-bottom: 14px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
  font-size: 12px;
  color: #d48806;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 空状态与快捷模板 */
.empty-quick-container {
  margin-top: 20px;
}

.empty-intro {
  text-align: center;
  padding: 16px 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #cbd5e1;
  margin-bottom: 16px;
}

.empty-icon {
  font-size: 28px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.empty-text {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.quick-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick-card {
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-card:hover {
  border-color: #1890ff;
  transform: translateX(3px);
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.12);
}

.quick-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.quick-card-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.quick-card-desc {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 错误提示 */
.error-container {
  margin-top: 16px;
}
.error-actions {
  margin-top: 10px;
  text-align: right;
}

/* 诊断结果容器 */
.result-container {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 2px solid #e2e8f0;
}

.result-header-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 6px;
}

.result-header-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confidence-tag {
  font-size: 11px;
}

/* 结果卡片 */
.result-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.card-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.summary-card {
  background: #f8fafc;
  border-left: 4px solid #1890ff;
}

.summary-content {
  font-size: 13px;
  line-height: 1.6;
  color: #1e293b;
  font-weight: 500;
}

.source-subtext {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
}

/* 原因列表 */
.causes-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cause-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}

.cause-index {
  min-width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #eff6ff;
  color: #1890ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  margin-top: 1px;
}

/* 排障步骤与命令 */
.checks-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.check-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
}

.check-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.check-icon {
  color: #10b981;
  font-weight: bold;
}

.check-title {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}

.check-desc {
  font-size: 12px;
  color: #475569;
  line-height: 1.4;
  margin-bottom: 6px;
}

.command-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1e293b;
  border-radius: 4px;
  padding: 6px 10px;
  margin-top: 4px;
}

.command-content {
  flex: 1;
  overflow-x: auto;
}

.command-content code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  color: #38bdf8;
  white-space: nowrap;
}

.copy-btn {
  color: #94a3b8;
  padding: 0 4px;
  margin-left: 8px;
}
.copy-btn:hover {
  color: #ffffff;
}

.command-security-notice {
  margin-top: 10px;
  font-size: 11px;
  color: #64748b;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  line-height: 1.4;
  padding: 6px 8px;
  background: #f1f5f9;
  border-radius: 4px;
}

/* 风险提示 */
.warnings-card {
  background: #fffbeb;
  border-color: #fef3c7;
}

.warning-label {
  color: #b45309;
}

.warnings-list {
  list-style: disc;
  padding-left: 18px;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #92400e;
  line-height: 1.5;
}

/* 诊断依据 */
.evidence-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.evidence-tag {
  font-size: 11px;
  border-radius: 4px;
}

/* 追问区域 */
.follow-up-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
}

.follow-up-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.follow-up-input-row {
  display: flex;
  gap: 8px;
}

.follow-up-reply-card {
  margin-top: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
}

.follow-up-reply-title {
  font-size: 12px;
  font-weight: 600;
  color: #1890ff;
  margin-bottom: 4px;
}

.follow-up-reply-answer {
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}
</style>
<template>
  <div>
    <div class="ai-fab" @click="drawerVisible = true" title="唤醒智能运维专家">
      <i class="el-icon-cpu"></i> ✨ AIOps
    </div>

    <el-drawer
      title="✨ Tinc 智能运维专家"
      :visible.sync="drawerVisible"
      direction="rtl"
      size="450px"
      custom-class="ai-drawer"
    >
      <div class="ai-container">
        <div class="input-section">
          <el-input
            size="small"
            v-model="queryForm.context"
            placeholder="例如：Windows 节点无法连接 Ubuntu 服务端"
            prefix-icon="el-icon-position"
          ></el-input>
          <div style="margin-top: 10px;"></div>
          <el-input
            type="textarea"
            :rows="4"
            v-model="queryForm.errorLog"
            placeholder="请在此粘贴冰冷的系统报错日志..."
          ></el-input>
          
          <el-button 
            type="primary" 
            icon="el-icon-magic-stick" 
            style="width: 100%; margin-top: 15px;" 
            @click="handleAnalyze"
            :loading="loading"
          >
            {{ loading ? 'DeepSeek 正在思考中...' : '开始深度诊断' }}
          </el-button>
        </div>

        <el-divider>诊断结果</el-divider>

        <div class="result-section" v-loading="loading">
          <div v-if="!aiResult" class="empty-tip">
            等待您的输入，随时准备排障...
          </div>
          <div v-else class="markdown-body" v-html="renderedMarkdown"></div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { marked } from 'marked'

export default {
  name: 'AiAssistant',
  data() {
    return {
      drawerVisible: false,
      loading: false,
      queryForm: {
        context: '',
        errorLog: ''
      },
      aiResult: ''
    }
  },
  computed: {
    renderedMarkdown() {
      if (!this.aiResult) return '';
      return marked(this.aiResult);
    }
  },
  methods: {
    handleAnalyze() {
      if (!this.queryForm.errorLog.trim()) {
        this.$message.warning("请至少提供报错日志！");
        return;
      }
      
      this.loading = true;
      
      // 【终极降维打击】使用浏览器原生 fetch API，彻底免疫 axios 报错！
      fetch('/ai-api/analyze', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          context: this.queryForm.context,
          error_log: this.queryForm.errorLog
        })
      })
      .then(response => response.json())
      .then(res => {
        // 提取 Python 后端返回的数据
        this.aiResult = res.data || res; 
        this.loading = false;
        this.$message.success("诊断完成！");
      })
      .catch(err => {
        this.loading = false;
        console.error("底层网络请求失败:", err);
        this.aiResult = "⚠️ **调用 AI 引擎失败**\n\n请检查网络或后端服务状态。";
      });
    }
  }
}
</script>

<style scoped>
.ai-fab {
  position: fixed;
  right: 20px;
  bottom: 80px;
  background-color: #1890ff;
  color: white;
  padding: 12px 20px;
  border-radius: 30px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.4);
  cursor: pointer;
  z-index: 9999;
  font-weight: bold;
  transition: all 0.3s;
}
.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(24, 144, 255, 0.6);
}
.ai-container {
  padding: 0 20px 20px 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.result-section {
  flex: 1;
  overflow-y: auto;
  background: #f8f9fa;
  border-radius: 8px;
  padding: 15px;
  border: 1px solid #ebeef5;
}
.empty-tip {
  color: #909399;
  text-align: center;
  margin-top: 50px;
}
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}
.markdown-body ::v-deep h1, 
.markdown-body ::v-deep h2, 
.markdown-body ::v-deep h3 {
  margin-top: 0;
  color: #1f2d3d;
}
.markdown-body ::v-deep p {
  margin-bottom: 10px;
}
.markdown-body ::v-deep strong {
  color: #ff4949;
}
</style>
<template>
  <div class="login-wrapper">
    <!-- 左侧科技展示区域 (~55%) -->
    <div class="login-brand-panel">
      <div class="brand-panel-header">
        <div class="brand-logo-badge">
          <svg class="brand-logo-svg" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="16" cy="16" r="14" stroke="#38bdf8" stroke-width="2" stroke-dasharray="4 2" />
            <circle cx="8" cy="16" r="3" fill="#38bdf8" />
            <circle cx="24" cy="16" r="3" fill="#38bdf8" />
            <circle cx="16" cy="8" r="3" fill="#10b981" />
            <circle cx="16" cy="24" r="3" fill="#60a5fa" />
            <line x1="8" y1="16" x2="24" y2="16" stroke="#38bdf8" stroke-width="1.5" />
            <line x1="16" y1="8" x2="16" y2="24" stroke="#38bdf8" stroke-width="1.5" />
            <circle cx="16" cy="16" r="4" fill="#0284c7" />
          </svg>
          <span class="brand-name">TincLink</span>
        </div>
        <span class="brand-version-tag">Network Control Platform</span>
      </div>

      <div class="brand-panel-content">
        <h1 class="brand-title">
          TincLink
          <span class="brand-title-sub">虚拟网络服务平台</span>
        </h1>
        <p class="brand-lead">
          面向跨域设备互联的虚拟网络服务平台
        </p>

        <!-- 核心能力特性 -->
        <div class="feature-cards">
          <div class="feature-card">
            <div class="feature-icon icon-access">
              <i class="el-icon-cpu"></i>
            </div>
            <div class="feature-info">
              <div class="feature-title">统一管控多接入节点</div>
              <div class="feature-desc">协同多集群 Access Server，支持本地直连与轻量 Agent 探针</div>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-secure">
              <i class="el-icon-connection"></i>
            </div>
            <div class="feature-info">
              <div class="feature-title">安全建立虚拟网络</div>
              <div class="feature-desc">全流量加密隔离、网段自主分配与稳定公钥路由</div>
            </div>
          </div>

          <div class="feature-card">
            <div class="feature-icon icon-manage">
              <i class="el-icon-s-operation"></i>
            </div>
            <div class="feature-info">
              <div class="feature-title">集中管理节点身份与状态</div>
              <div class="feature-desc">零信任设备准入、密钥生命周期治理与运行时就绪探针</div>
            </div>
          </div>
        </div>

        <!-- 极简网络拓扑线框动效 -->
        <div class="topology-preview">
          <svg class="topology-svg" viewBox="0 0 480 130" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- 连线 -->
            <path d="M 60 65 L 170 35" stroke="#1e3a8a" stroke-width="1.5" stroke-dasharray="3 3" />
            <path d="M 60 65 L 170 95" stroke="#1e3a8a" stroke-width="1.5" stroke-dasharray="3 3" />
            <path d="M 170 35 L 310 35" stroke="#0284c7" stroke-width="2" />
            <path d="M 170 95 L 310 95" stroke="#0284c7" stroke-width="2" />
            <path d="M 310 35 L 420 65" stroke="#1e3a8a" stroke-width="1.5" stroke-dasharray="3 3" />
            <path d="M 310 95 L 420 65" stroke="#1e3a8a" stroke-width="1.5" stroke-dasharray="3 3" />
            <path d="M 170 35 L 310 95" stroke="#0369a1" stroke-width="1" stroke-opacity="0.6" />
            <path d="M 170 95 L 310 35" stroke="#0369a1" stroke-width="1" stroke-opacity="0.6" />

            <!-- Access Nodes -->
            <g class="node-group">
              <circle cx="60" cy="65" r="14" fill="#0f172a" stroke="#38bdf8" stroke-width="1.5" />
              <circle cx="60" cy="65" r="4" fill="#38bdf8" />
              <text x="60" y="95" fill="#94a3b8" font-size="11" text-anchor="middle" font-family="monospace">Aliyun_tinc</text>
            </g>

            <g class="node-group">
              <circle cx="170" cy="35" r="14" fill="#0f172a" stroke="#10b981" stroke-width="1.5" />
              <circle cx="170" cy="35" r="4" fill="#10b981" />
              <text x="170" y="20" fill="#94a3b8" font-size="11" text-anchor="middle" font-family="monospace">Access-A</text>
            </g>

            <g class="node-group">
              <circle cx="170" cy="95" r="14" fill="#0f172a" stroke="#10b981" stroke-width="1.5" />
              <circle cx="170" cy="95" r="4" fill="#10b981" />
              <text x="170" y="122" fill="#94a3b8" font-size="11" text-anchor="middle" font-family="monospace">Access-B</text>
            </g>

            <!-- Network Hubs -->
            <g class="node-group pulse-node">
              <circle cx="310" cy="35" r="16" fill="#0284c7" fill-opacity="0.2" stroke="#38bdf8" stroke-width="2" />
              <circle cx="310" cy="35" r="5" fill="#38bdf8" />
              <text x="310" y="20" fill="#bae6fd" font-size="11" text-anchor="middle" font-family="monospace">network1</text>
            </g>

            <g class="node-group pulse-node">
              <circle cx="310" cy="95" r="16" fill="#0284c7" fill-opacity="0.2" stroke="#38bdf8" stroke-width="2" />
              <circle cx="310" cy="95" r="5" fill="#38bdf8" />
              <text x="310" y="122" fill="#bae6fd" font-size="11" text-anchor="middle" font-family="monospace">new_network</text>
            </g>

            <g class="node-group">
              <circle cx="420" cy="65" r="12" fill="#0f172a" stroke="#60a5fa" stroke-width="1.5" />
              <circle cx="420" cy="65" r="3" fill="#60a5fa" />
              <text x="420" y="95" fill="#94a3b8" font-size="11" text-anchor="middle" font-family="monospace">Node-Client</text>
            </g>
          </svg>
        </div>
      </div>

      <div class="brand-panel-footer">
        <span>TincLink Architecture · Multi-Access Mesh & Runtime Router</span>
      </div>
    </div>

    <!-- 右侧登录面板 (~45%) -->
    <div class="login-form-panel">
      <div class="login-card-container">
        <div class="login-card-header">
          <div class="login-badge-wrap">
            <span class="system-badge">管理后台系统</span>
          </div>
          <h2 class="card-main-title">欢迎登录</h2>
          <p class="card-sub-title">请输入管理账号以接入 TincLink 控制中心</p>
        </div>

        <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form" size="medium">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="请输入登录账号"
            >
              <i slot="prefix" class="el-icon-user input-icon" />
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              auto-complete="off"
              placeholder="请输入密码"
              @keyup.enter.native="handleLogin"
            >
              <i slot="prefix" class="el-icon-lock input-icon" />
            </el-input>
          </el-form-item>

          <el-form-item prop="code" v-if="captchaEnabled">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.code"
                auto-complete="off"
                placeholder="请输入验证码"
                class="captcha-input"
                @keyup.enter.native="handleLogin"
              >
                <i slot="prefix" class="el-icon-key input-icon" />
              </el-input>
              <div class="captcha-img-wrap" title="点击刷新验证码" @click="getCode">
                <img :src="codeUrl" class="captcha-img" alt="验证码" />
              </div>
            </div>
          </el-form-item>

          <div class="login-extra-row">
            <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            <span class="security-tip"><i class="el-icon-lock"></i> 生产加密通道</span>
          </div>

          <el-form-item style="margin-top: 15px; margin-bottom: 0;">
            <el-button
              :loading="loading"
              size="medium"
              type="primary"
              class="login-submit-btn"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登 录</span>
              <span v-else>正在接入控制中心...</span>
            </el-button>
          </el-form-item>

          <div class="register-row" v-if="register">
            <router-link class="link-type" :to="'/register'">立即注册</router-link>
          </div>
        </el-form>

        <div class="login-card-footer">
          <span>{{ footerContent || 'TincLink · 面向跨域设备互联的虚拟网络服务平台' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from '@/utils/jsencrypt'
import defaultSettings from '@/settings'

export default {
  name: "Login",
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      footerContent: defaultSettings.footerContent,
      codeUrl: "",
      loginForm: {
        username: "admin",
        password: "admin123",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      },
      loading: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = "data:image/gif;base64," + res.img
          this.loginForm.uuid = res.uuid
        }
      })
    },
    getCookie() {
      const username = Cookies.get("username")
      const password = Cookies.get("password")
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      }
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 })
            Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 })
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
          } else {
            Cookies.remove("username")
            Cookies.remove("password")
            Cookies.remove('rememberMe')
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{})
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.login-wrapper {
  display: flex;
  min-height: 100vh;
  width: 100%;
  background-color: #0b1329;
  overflow: hidden;
}

/* 左侧品牌科技展示面板 (~55%) */
.login-brand-panel {
  flex: 55;
  background: radial-gradient(circle at 10% 20%, #0e1e38 0%, #090e1a 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  padding: 40px 50px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: 
      linear-gradient(rgba(56, 189, 248, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(56, 189, 248, 0.03) 1px, transparent 1px);
    background-size: 32px 32px;
    pointer-events: none;
  }
}

.brand-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;

  .brand-logo-badge {
    display: flex;
    align-items: center;
    gap: 12px;

    .brand-logo-svg {
      width: 34px;
      height: 34px;
    }

    .brand-name {
      font-size: 22px;
      font-weight: 700;
      color: #ffffff;
      letter-spacing: 0.5px;
    }
  }

  .brand-version-tag {
    font-size: 11px;
    font-family: monospace;
    color: #38bdf8;
    background: rgba(2, 132, 199, 0.15);
    border: 1px solid rgba(56, 189, 248, 0.3);
    padding: 3px 8px;
    border-radius: 4px;
  }
}

.brand-panel-content {
  position: relative;
  z-index: 1;
  max-width: 600px;
  margin: 30px 0;

  .brand-title {
    font-size: 32px;
    font-weight: 700;
    color: #ffffff;
    margin: 0 0 8px 0;
    line-height: 1.2;

    .brand-title-sub {
      display: block;
      font-size: 18px;
      font-weight: 500;
      color: #38bdf8;
      margin-top: 6px;
    }
  }

  .brand-lead {
    font-size: 14px;
    color: #94a3b8;
    margin: 0 0 30px 0;
    line-height: 1.6;
  }
}

.feature-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 30px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 16px;
  backdrop-filter: blur(8px);
  transition: all 0.2s ease;

  &:hover {
    border-color: rgba(56, 189, 248, 0.3);
    background: rgba(15, 23, 42, 0.85);
    transform: translateX(4px);
  }

  .feature-icon {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    flex-shrink: 0;

    &.icon-access {
      background: rgba(2, 132, 199, 0.2);
      color: #38bdf8;
    }

    &.icon-secure {
      background: rgba(16, 185, 129, 0.2);
      color: #34d399;
    }

    &.icon-manage {
      background: rgba(139, 92, 246, 0.2);
      color: #a78bfa;
    }
  }

  .feature-info {
    .feature-title {
      font-size: 14px;
      font-weight: 600;
      color: #f1f5f9;
      margin-bottom: 3px;
    }

    .feature-desc {
      font-size: 12px;
      color: #94a3b8;
      line-height: 1.4;
    }
  }
}

.topology-preview {
  background: rgba(11, 19, 43, 0.5);
  border: 1px solid rgba(56, 189, 248, 0.15);
  border-radius: 8px;
  padding: 12px 16px;

  .topology-svg {
    width: 100%;
    height: auto;
    display: block;
  }
}

.pulse-node {
  animation: pulse-glow 3s infinite alternate ease-in-out;
}

@keyframes pulse-glow {
  0% {
    transform-origin: center;
    opacity: 0.8;
  }
  100% {
    transform-origin: center;
    opacity: 1;
  }
}

.brand-panel-footer {
  font-size: 12px;
  color: #64748b;
  position: relative;
  z-index: 1;
}

/* 右侧登录面板 (~45%) */
.login-form-panel {
  flex: 45;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background-color: #f8fafc;
}

.login-card-container {
  width: 420px;
  max-width: 100%;
  background: #ffffff;
  border-radius: 12px;
  padding: 36px 32px 24px 32px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.03);
  border: 1px solid #e2e8f0;
}

.login-card-header {
  margin-bottom: 26px;

  .login-badge-wrap {
    margin-bottom: 10px;

    .system-badge {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      color: #0284c7;
      background: #e0f2fe;
      padding: 2px 8px;
      border-radius: 4px;
      letter-spacing: 0.5px;
    }
  }

  .card-main-title {
    font-size: 22px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 6px 0;
  }

  .card-sub-title {
    font-size: 13px;
    color: #64748b;
    margin: 0;
  }
}

.login-form {
  .el-input {
    height: 42px;
    input {
      height: 42px;
      background-color: #f8fafc;
      border-color: #cbd5e1;
      border-radius: 6px;
      color: #0f172a;
      transition: all 0.2s;

      &:focus {
        border-color: #0284c7;
        background-color: #ffffff;
      }
    }
  }

  .input-icon {
    font-size: 15px;
    margin-left: 5px;
    color: #94a3b8;
    line-height: 42px;
  }
}

.captcha-row {
  display: flex;
  gap: 12px;
  align-items: center;

  .captcha-input {
    flex: 1;
  }

  .captcha-img-wrap {
    width: 120px;
    height: 42px;
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid #cbd5e1;
    cursor: pointer;
    flex-shrink: 0;
    background: #f1f5f9;

    .captcha-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }
}

.login-extra-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 13px;

  .security-tip {
    font-size: 12px;
    color: #64748b;
  }
}

.login-submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 6px;
  background-color: #0284c7;
  border-color: #0284c7;
  letter-spacing: 2px;
  box-shadow: 0 4px 6px -1px rgba(2, 132, 199, 0.2);
  transition: all 0.2s;

  &:hover {
    background-color: #0369a1;
    border-color: #0369a1;
    box-shadow: 0 6px 10px -1px rgba(2, 132, 199, 0.3);
  }
}

.register-row {
  text-align: right;
  margin-top: 10px;
  font-size: 13px;

  .link-type {
    color: #0284c7;
    &:hover {
      text-decoration: underline;
    }
  }
}

.login-card-footer {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #f1f5f9;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

/* 响应式断点 */
@media (max-width: 992px) {
  .login-wrapper {
    flex-direction: column;
  }

  .login-brand-panel {
    flex: none;
    padding: 30px 20px;
  }

  .login-form-panel {
    flex: none;
    padding: 30px 20px;
  }
}
</style>

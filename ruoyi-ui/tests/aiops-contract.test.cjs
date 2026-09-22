const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')

test('AIOps client-side sanitizer redacts all sensitive credentials and private keys', async () => {
  const { sanitizeDiagnosticInput } = await import('../src/utils/aiopsSanitizer.js')

  const testPayload = `
[DEBUG] Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l
[TOKEN] Bearer myValidToken1234567890
[JWT] eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signatureValid123
[AUTH] password=SuperSecretPassword123!
[AGENT] agentSecret=ag_sec_9988776655443322
[COOKIE] Set-Cookie: SESSIONID=abcdef123456; Path=/; Secure; HttpOnly
[SSH/TLS] Key Content:
-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEAz81r9k2j0wida91238912301
-----END RSA PRIVATE KEY-----
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSk
-----END PRIVATE KEY-----
[INFO] Windows client unable to connect to Ubuntu server DEV_B.
`

  const result = sanitizeDiagnosticInput(testPayload)

  // 1. 绝对禁止私钥内容泄露
  assert.equal(result.sanitizedText.includes('MIIEowIBAAKCAQEAz81r9k2j0wida91238912301'), false)
  assert.equal(result.sanitizedText.includes('MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSk'), false)
  assert.equal(result.sanitizedText.includes('[REDACTED: PRIVATE KEY]'), true)

  // 2. 密码与 Secret 脱敏
  assert.equal(result.sanitizedText.includes('SuperSecretPassword123!'), false)
  assert.equal(result.sanitizedText.includes('password=[REDACTED]'), true)
  assert.equal(result.sanitizedText.includes('ag_sec_9988776655443322'), false)
  assert.equal(result.sanitizedText.includes('agentSecret=[REDACTED]'), true)

  // 3. Authorization、Bearer、JWT 与 Cookie 脱敏
  assert.equal(result.sanitizedText.includes('YWxhZGRpbjpvcGVuc2VzYW1l'), false)
  assert.equal(result.sanitizedText.includes('Authorization: [REDACTED]'), true)
  assert.equal(result.sanitizedText.includes('myValidToken1234567890'), false)
  assert.equal(result.sanitizedText.includes('Bearer [REDACTED]'), true)
  assert.equal(result.sanitizedText.includes('signatureValid123'), false)
  assert.equal(result.sanitizedText.includes('[REDACTED: JWT]'), true)
  assert.equal(result.sanitizedText.includes('SESSIONID=abcdef123456'), false)
  assert.equal(result.sanitizedText.includes('Set-Cookie: [REDACTED]'), true)

  // 4. 正常故障日志保持完整
  assert.equal(result.sanitizedText.includes('Windows client unable to connect to Ubuntu server DEV_B.'), true)
  assert.ok(result.redactedCount >= 7)
})

test('AIOps API contract strictly uses Stable IDs (serverId, networkId, nodeId)', () => {
  const apiFile = read('src/api/tinc/aiops.js')
  const viewFile = read('src/components/AiAssistant/index.vue')

  // API 契约路径校验
  assert.match(apiFile, /url:\s*['"]\/tinc\/aiops\/diagnose['"]/)
  assert.match(apiFile, /method:\s*['"]post['"]/)

  // Context 数据结构必须使用 Stable ID，禁止使用名称作为主键
  assert.match(apiFile, /context\.serverId/)
  assert.match(apiFile, /context\.networkId/)
  assert.match(apiFile, /context\.nodeId/)

  // 前端组件构建请求时绑定 Stable ID
  assert.match(viewFile, /serverId:\s*this\.contextForm\.serverId/)
  assert.match(viewFile, /networkId:\s*this\.contextForm\.networkId/)
  assert.match(viewFile, /nodeId:\s*this\.contextForm\.nodeId/)
})

test('AIOps diagnostic results enforce structured output with evidence and checks', () => {
  const apiFile = read('src/api/tinc/aiops.js')
  const viewFile = read('src/components/AiAssistant/index.vue')

  // 校验必须包含五个核心输出维度
  assert.match(apiFile, /summary:/)
  assert.match(apiFile, /possibleCauses:/)
  assert.match(apiFile, /checks:/)
  assert.match(apiFile, /warnings:/)
  assert.match(apiFile, /evidence/)

  // 前端模板必须渲染对应的结构卡片
  assert.match(viewFile, /class="result-card summary-card"/)
  assert.match(viewFile, /class="result-card causes-card"/)
  assert.match(viewFile, /class="result-card checks-card"/)
  assert.match(viewFile, /class="result-card warnings-card"/)
  assert.match(viewFile, /class="result-card evidence-card"/)
})

test('AIOps strictly forbids shell execution and unsafe raw HTML injection', () => {
  const viewFile = read('src/components/AiAssistant/index.vue')

  // 严禁 /exec 与远程终端调用
  assert.doesNotMatch(viewFile, /\/exec\b/)
  assert.doesNotMatch(viewFile, /executeCommand/)
  assert.doesNotMatch(viewFile, /terminal/)
  assert.doesNotMatch(viewFile, /runShell/)

  // 严禁未经净化的 v-html 用于大模型/用户输出
  assert.doesNotMatch(viewFile, /v-html/)

  // 必须明确安全警示文案
  assert.match(viewFile, /AIOps 仅建议排障命令，系统严禁远程执行任意 Shell/)
})

test('AIOps provides empty state with required 4 quick diagnostic templates', () => {
  const apiFile = read('src/api/tinc/aiops.js')
  const viewFile = read('src/components/AiAssistant/index.vue')

  // 模板库中必须包含 4 个标准快捷问题
  assert.match(apiFile, /Windows 客户端无法连接/)
  assert.match(apiFile, /Network 为什么显示 NOT_READY？/)
  assert.match(apiFile, /Access Agent 为什么 UNREACHABLE？/)
  assert.match(apiFile, /节点为什么 Ping 不通网关？/)

  // 组件支持模板应用与清空重置
  assert.match(viewFile, /applyQuickTemplate/)
  assert.match(viewFile, /handleResetAll/)
  assert.match(viewFile, /clearLogs/)
})

test('AIOps drawer respects responsive sizing and floating button toggle', () => {
  const viewFile = read('src/components/AiAssistant/index.vue')

  // 抽屉与浮动按钮绑定
  assert.match(viewFile, /v-show="!drawerVisible"/)
  assert.match(viewFile, /class="aiops-fab"/)
  assert.match(viewFile, /class="aiops-drawer"/)

  // 响应式尺寸样式
  assert.match(viewFile, /@media screen and \(max-width: 768px\)/)
  assert.match(viewFile, /@media screen and \(min-width: 1441px\)/)
})

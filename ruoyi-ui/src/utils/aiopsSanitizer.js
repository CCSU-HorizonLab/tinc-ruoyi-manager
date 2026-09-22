/**
 * TincLink AIOps 客户端敏感信息安全脱敏工具
 * 严格保护服务器与客户端机密安全，禁止将私钥、Token、密码、凭证等发送至任何诊断服务。
 */

// 匹配各类 PEM 格式私钥 (PKCS#8, PKCS#1 RSA, EC, OPENSSH, DSA 等)
const PRIVATE_KEY_REGEX = /-----BEGIN (?:[A-Z0-9_-]+ )?PRIVATE KEY-----[\s\S]*?-----END (?:[A-Z0-9_-]+ )?PRIVATE KEY-----/gi;

// 匹配 Bearer Token
const BEARER_TOKEN_REGEX = /Bearer\s+[A-Za-z0-9\-._~+/]+=*/gi;

// 匹配标准 JWT (通常第一段以 eyJ 开头)
const JWT_REGEX = /\beyJ[A-Za-z0-9-_]{8,}\.[A-Za-z0-9-_]{8,}(?:\.[A-Za-z0-9-_.]*)?\b/g;

// 匹配 Authorization / Proxy-Authorization 头部
const AUTH_HEADER_REGEX = /(?:Authorization|Proxy-Authorization)\s*:\s*[^\r\n]+/gi;

// 匹配密码字段 (如 password=xxx, pwd: xxx, "password": "xxx")
const PASSWORD_FIELD_REGEX = /(?:password|passwd|pwd)\s*[:=]\s*["']?([^\s,;&"']+)["']?/gi;

// 匹配 Secret 字段 (如 secret=xxx, agentSecret=xxx, agent_secret: xxx)
const SECRET_FIELD_REGEX = /(?:agentSecret|agent_secret|secretKey|secret_key|app_secret|client_secret|secret)\s*[:=]\s*["']?([^\s,;&"']+)["']?/gi;

// 匹配 Cookie / Set-Cookie 头部
const COOKIE_HEADER_REGEX = /(?:Set-Cookie|Cookie)\s*:\s*[^\r\n]+/gi;

/**
 * 对输入的日志或描述进行敏感信息检测与脱敏
 * @param {string} rawText 原始文本
 * @returns {{ sanitizedText: string, redactedCount: number, detectedTypes: string[] }}
 */
export function sanitizeDiagnosticInput(rawText) {
  if (!rawText || typeof rawText !== 'string') {
    return {
      sanitizedText: '',
      redactedCount: 0,
      detectedTypes: []
    };
  }

  let text = rawText;
  let redactedCount = 0;
  const detectedTypes = [];

  // 1. Private Key (最高安全级，严禁任何私钥内容泄露)
  if (PRIVATE_KEY_REGEX.test(text)) {
    detectedTypes.push('Private Key');
    text = text.replace(PRIVATE_KEY_REGEX, () => {
      redactedCount++;
      return '[REDACTED: PRIVATE KEY]';
    });
  }

  // 2. Authorization Header
  if (AUTH_HEADER_REGEX.test(text)) {
    detectedTypes.push('Authorization Header');
    text = text.replace(AUTH_HEADER_REGEX, () => {
      redactedCount++;
      return 'Authorization: [REDACTED]';
    });
  }

  // 3. Bearer Token
  if (BEARER_TOKEN_REGEX.test(text)) {
    detectedTypes.push('Bearer Token');
    text = text.replace(BEARER_TOKEN_REGEX, () => {
      redactedCount++;
      return 'Bearer [REDACTED]';
    });
  }

  // 4. JWT
  if (JWT_REGEX.test(text)) {
    detectedTypes.push('JWT Token');
    text = text.replace(JWT_REGEX, () => {
      redactedCount++;
      return '[REDACTED: JWT]';
    });
  }

  // 5. Password
  if (PASSWORD_FIELD_REGEX.test(text)) {
    detectedTypes.push('Password');
    text = text.replace(PASSWORD_FIELD_REGEX, (match, p1) => {
      redactedCount++;
      return match.replace(p1, '[REDACTED]');
    });
  }

  // 6. Secret / Agent Secret
  if (SECRET_FIELD_REGEX.test(text)) {
    detectedTypes.push('Agent/API Secret');
    text = text.replace(SECRET_FIELD_REGEX, (match, p1) => {
      redactedCount++;
      return match.replace(p1, '[REDACTED]');
    });
  }

  // 7. Cookie / Set-Cookie
  if (COOKIE_HEADER_REGEX.test(text)) {
    detectedTypes.push('Cookie Header');
    text = text.replace(COOKIE_HEADER_REGEX, (match) => {
      redactedCount++;
      const prefix = match.split(':')[0];
      return `${prefix}: [REDACTED]`;
    });
  }

  return {
    sanitizedText: text,
    redactedCount,
    detectedTypes: Array.from(new Set(detectedTypes))
  };
}

export default {
  sanitizeDiagnosticInput
}

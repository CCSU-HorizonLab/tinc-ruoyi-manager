import request from '@/utils/request'

/**
 * TincLink AIOps 智能运维诊断接口契约
 * POST /tinc/aiops/diagnose
 *
 * @param {Object} data 诊断请求参数
 * @param {string} data.issue 故障现象描述 (1~200字)
 * @param {string} data.logs 报错日志/终端输出
 * @param {Object} data.context 拓扑上下文 (必须使用 Stable-ID，禁止使用名称作为主键)
 * @param {number} [data.context.serverId] 关联服务器 Stable ID
 * @param {number} [data.context.networkId] 关联虚拟网络 Stable ID
 * @param {number} [data.context.nodeId] 关联节点 Stable ID
 */
export function diagnoseIssue(data) {
  return request({
    url: '/tinc/aiops/diagnose',
    method: 'post',
    data: data,
    timeout: 15000
  })
}

/**
 * 快捷排障问题模板库
 */
export function getAiOpsQuickTemplates() {
  return [
    {
      id: 'windows-connect',
      title: 'Windows 客户端无法连接',
      tag: 'Windows',
      tagType: 'primary',
      issue: 'Windows 节点无法连接 Ubuntu 接入服务器，状态一直停留在 Connecting。',
      logs: `2026-09-22 20:15:01 [Tinc-Client] ERROR: Cannot open TAP-Win32/Wintun device.
2026-09-22 20:15:03 [Tinc-Client] WARNING: Failed to reach Access-B at 203.0.113.10:655.
2026-09-22 20:15:05 [Tinc-Client] Connection timed out after 3 retries.`
    },
    {
      id: 'network-not-ready',
      title: 'Network 为什么显示 NOT_READY？',
      tag: 'Runtime',
      tagType: 'danger',
      issue: 'DEV_B 虚拟网络在管理端显示 NOT_READY，无法转发任何流量。',
      logs: `● tinc@DEV_B.service - Tinc VPN for net DEV_B
   Loaded: loaded (/etc/systemd/system/tinc@DEV_B.service)
   Active: failed (Result: exit-code) since Tue 2026-09-22 19:40:12 CST; 32min ago
  Process: 14221 ExecStart=/usr/sbin/tincd -n DEV_B (code=exited, status=1/FAILURE)
   tincd[14221]: Address 0.0.0.0:655 already in use!
   tincd[14221]: Terminating`
    },
    {
      id: 'agent-unreachable',
      title: 'Access Agent 为什么 UNREACHABLE？',
      tag: 'Agent',
      tagType: 'warning',
      issue: 'Access-B 接入服务器的管理探针状态显示 UNREACHABLE，无法下发配置。',
      logs: `[AgentProbe] Probe connection to https://access-b.tinclink.local:9091/probe failed.
Error: connect ETIMEDOUT 198.51.100.22:9091
Last probe timestamp: 2026-09-22T18:20:00Z.
Node configurations cannot be synchronized while agent is offline.`
    },
    {
      id: 'ping-gateway-fail',
      title: '节点为什么 Ping 不通网关？',
      tag: '数据面',
      tagType: 'info',
      issue: 'DEV_B 显示 READY，Node client_b 已配置，但客户端无法 Ping 通网关 10.0.0.1。',
      logs: `PING 10.0.0.1 (10.0.0.1) 56(84) bytes of data.
From 10.0.0.5 icmp_seq=1 Destination Host Unreachable
From 10.0.0.5 icmp_seq=2 Destination Host Unreachable
From 10.0.0.5 icmp_seq=3 Destination Host Unreachable
--- 10.0.0.1 ping statistics ---
3 packets transmitted, 0 received, +3 errors, 100% packet loss`
    }
  ]
}

/**
 * TincLink 内置专家规则诊断引擎 (Rule-based Diagnostic Engine)
 * 当未配置远程大模型服务时，依据 TincLink 权威网络拓扑状态与故障知识库提供结构化排障方案。
 * 绝不把模拟结果伪装成 AI 实时分析，明确标示为内置专家规则诊断。
 *
 * @param {string} issue 故障现象
 * @param {string} logs 日志
 * @param {Object} context 拓扑上下文
 * @returns {Object} 结构化诊断结论
 */
export function ruleBasedDiagnose(issue = '', logs = '', context = {}) {
  const text = `${issue || ''} ${logs || ''}`.toLowerCase();

  // 1. 收集真实网络拓扑依据 (Evidence)
  const evidence = [];
  if (context && context.serverId) {
    const sName = context.serverName || `Server #${context.serverId}`;
    const sStatus = context.agentStatus || 'READY';
    evidence.push(`接入服务器 ${sName} (ID: ${context.serverId}) [探针: ${sStatus}]`);
  }
  if (context && context.networkId) {
    const nName = context.networkName || `Network #${context.networkId}`;
    const nReadiness = context.readiness || 'READY';
    const nSegment = context.segment ? ` | 网段: ${context.segment}` : '';
    evidence.push(`虚拟网络 ${nName} (ID: ${context.networkId}) [运行态: ${nReadiness}${nSegment}]`);
  }
  if (context && context.nodeId) {
    const nodeName = context.nodeName || `Node #${context.nodeId}`;
    const nConfig = context.configStatus || 'CONFIGURED';
    evidence.push(`目标节点 ${nodeName} (ID: ${context.nodeId}) [状态: ${nConfig}]`);
  }
  if (evidence.length === 0) {
    evidence.push('未绑定特定拓扑上下文，基于 TincLink 通用网络排障模型');
  }

  // 2. Windows 客户端专项规则
  if (text.includes('windows') || text.includes('tap') || text.includes('wintun') || text.includes('adapter') || text.includes('tap0901')) {
    return {
      engine: 'RULE_BASED',
      source: 'TincLink 规则知识库 (Windows 专项)',
      summary: 'Windows 节点虚拟网卡 (TAP/Wintun) 驱动或路由策略异常，导致无法建立安全隧道。',
      confidence: 'HIGH',
      statusType: 'danger',
      possibleCauses: [
        'TAP-Windows 或 Wintun 虚拟网卡驱动未正确安装或处于禁用状态',
        'Windows 防火墙拦截了 Tinc 守护进程的入站/出站 TCP/UDP 655 端口流量',
        '客户端 tinc.conf 中 Address / Port 配置与 Ubuntu 接入服务器公网 IP 不匹配',
        '本地网络与 Tinc 分配的子网网段发生 IP 冲突或网关路由未注入'
      ],
      checks: [
        {
          title: '检查 Windows 虚拟适配器状态',
          description: '以管理员身份打开 PowerShell，确认 TAP/Wintun 适配器是否存在且已启用。',
          command: 'Get-NetAdapter | Where-Object { $_.InterfaceDescription -match "TAP|Wintun" }'
        },
        {
          title: '验证接入服务器端口可达性',
          description: '测试 Access Server 监听的 Tinc 端口（默认 655）连通性。',
          command: 'Test-NetConnection -ComputerName <AccessServerIP> -Port 655'
        },
        {
          title: '检查 Windows 客户端日志',
          description: '查看 Windows Agent 服务或 tinc 守护进程日志中的握手报错。',
          command: 'Get-Content -Path "C:\\Program Files\\tinc\\tinc.log" -Tail 20'
        }
      ],
      warnings: [
        '安装或重启虚拟网卡驱动需要管理员权限 (Run as Administrator)',
        '请确保接入端与客户端的 RSA/Ed25519 公钥已完成双向同步，否则握手将静默超时'
      ],
      evidence
    };
  }

  // 3. Network NOT_READY 专项规则
  if (text.includes('not_ready') || text.includes('readiness') || text.includes('runtime') || text.includes('address already in use') || text.includes('failed to start') || text.includes('status=1/failure')) {
    return {
      engine: 'RULE_BASED',
      source: 'TincLink 规则知识库 (Network 运行态)',
      summary: '虚拟网络 Runtime 处于 NOT_READY 状态，服务端守护进程未就绪或端口冲突。',
      confidence: 'HIGH',
      statusType: 'danger',
      possibleCauses: [
        'Access Server 上的 systemd 服务 (tinc@<net>) 启动失败或意外崩溃',
        '服务器配置端口 (如 655) 已被其他进程占用或旧守护进程残留',
        '网络接口启动脚本 (tinc-up) 执行权限缺失或缺少 iproute2 工具',
        'Access Agent 无法与管理端正常上报运行探针'
      ],
      checks: [
        {
          title: '检查 Linux 服务端 systemd 运行日志',
          description: '查看 systemd 管理的 tinc 网络单元状态与最近错误。',
          command: 'systemctl status tinc@<NetworkName> --no-pager -l'
        },
        {
          title: '核验 655 端口 TCP/UDP 监听状态',
          description: '确认没有端口冲突，且监听地址为 0.0.0.0。',
          command: 'ss -tulnp | grep :655'
        },
        {
          title: '排查 tinc-up 网卡启动脚本权限',
          description: '确保 tinc-up 具备可执行权限 (chmod +x)。',
          command: 'ls -la /etc/tinc/<NetworkName>/tinc-up'
        }
      ],
      warnings: [
        '服务重启将导致当前网络内所有节点临时断开隧道约 2~5 秒',
        'AIOps 仅提供安全命令建议，请由具备主机运维权限的管理员手动核对后执行'
      ],
      evidence
    };
  }

  // 4. Agent UNREACHABLE 专项规则
  if (text.includes('unreachable') || text.includes('agent') || text.includes('probe') || text.includes('离线') || text.includes('etimedout')) {
    return {
      engine: 'RULE_BASED',
      source: 'TincLink 规则知识库 (Agent 探针链路)',
      summary: 'Access Server 上的 Agent 探针离线或网络不可达，管理端无法同步网络拓扑。',
      confidence: 'HIGH',
      statusType: 'danger',
      possibleCauses: [
        'Access Agent 进程 (tinclink-access-agent) 未启动或已被系统 OOM Killer 终止',
        '管理端向 Agent 发起的探针通信端口 (默认 9091) 受云安全组或防火墙拦截',
        'Agent 端管理凭证 (Agent Secret) 与平台记录不一致导致鉴权失败',
        '服务器系统时间与管理端偏差超过 300 秒导致签名过期'
      ],
      checks: [
        {
          title: '检查 Agent 守护进程状态',
          description: '在目标接入服务器上确认 tinclink-access-agent 是否在活跃运行。',
          command: 'systemctl status tinclink-access-agent'
        },
        {
          title: '排查 Agent 监听端口与防火墙',
          description: '检查 9091 探针接口监听及 ufw/iptables 放行状态。',
          command: 'ufw status | grep 9091 || iptables -L -n -v | grep 9091'
        },
        {
          title: '查看 Agent 实时运行日志',
          description: '获取最近 50 行 Agent 日志以排查异常堆栈。',
          command: 'journalctl -u tinclink-access-agent -n 50 --no-pager'
        }
      ],
      warnings: [
        '若 Agent 长期离线，新节点下发配置将挂起直至 Agent 恢复通信',
        '切勿将 Agent Secret 明文输出或上传至公开仓库'
      ],
      evidence
    };
  }

  // 5. Ping 不通网关 / 数据面专项规则
  if (text.includes('ping') || text.includes('gateway') || text.includes('网关') || text.includes('icmp') || text.includes('packet loss') || text.includes('不通') || text.includes('destination host unreachable')) {
    return {
      engine: 'RULE_BASED',
      source: 'TincLink 规则知识库 (数据面通信)',
      summary: '节点控制面连接可能已建立，但虚拟网络数据平面路由或 ARP 转发受阻。',
      confidence: 'MEDIUM',
      statusType: 'warning',
      possibleCauses: [
        '节点本地路由表缺失直连虚拟子网的路由规则',
        '接入服务器 Linux 内核未开启 IP 转发 (net.ipv4.ip_forward = 1)',
        'iptables / nftables 防火墙默认 DROP 了 Forward 链的虚拟接口流量',
        '节点分发的 Subnet 与服务端 hosts 记录中的 Subnet 定义不符'
      ],
      checks: [
        {
          title: '检查服务端 IP 转发内核参数',
          description: '确认 Linux 内核是否允许数据包转发。',
          command: 'sysctl net.ipv4.ip_forward'
        },
        {
          title: '核验虚拟网卡 IP 与子网掩码配置',
          description: '检查虚拟接口分配的内网 IP 是否属于网络既定网段。',
          command: 'ip addr show dev tinc.<NetworkName>'
        },
        {
          title: '排查防火墙转发链 (FORWARD Chain)',
          description: '确认 iptables FORWARD 策略未全量阻断。',
          command: 'iptables -L FORWARD -n -v'
        }
      ],
      warnings: [
        '修改 sysctl 内核参数需要 root 权限，临时生效可用 sysctl -w',
        '若开启了多网段路由，请确保客户端子网掩码配置一致'
      ],
      evidence
    };
  }

  // 6. 公钥 / 凭据 / 权限专项规则
  if (text.includes('public key') || text.includes('key') || text.includes('rsa') || text.includes('ed25519') || text.includes('revoke') || text.includes('permission denied')) {
    return {
      engine: 'RULE_BASED',
      source: 'TincLink 规则知识库 (安全凭据)',
      summary: '节点身份认证失败或公钥文件不同步，Tinc 握手阶段被服务端主动重置。',
      confidence: 'HIGH',
      statusType: 'danger',
      possibleCauses: [
        '节点公钥文件未同步至服务端 /etc/tinc/<net>/hosts/ 目录中',
        '公钥格式错误或末尾缺少换行符导致 Tinc 解析器静默忽略',
        '该节点此前已被管理员执行过 Revoke (吊销) 操作，证书已列入黑名单',
        'hosts 目录权限不当导致 tincd 守护进程 (nobody 降权) 无法读取'
      ],
      checks: [
        {
          title: '校验服务端 hosts 目录公钥文件',
          description: '检查目标节点的公钥文件是否存在且非空。',
          command: 'ls -l /etc/tinc/<NetworkName>/hosts/<NodeName>'
        },
        {
          title: '核查 hosts 文件读取权限',
          description: '确保 tinc 进程拥有只读权限 (建议 644)。',
          command: 'chmod 644 /etc/tinc/<NetworkName>/hosts/*'
        }
      ],
      warnings: [
        '被 Revoke 吊销的节点无法再次连接，需生成新 Token 重建节点',
        '禁止在任何工单或诊断会话中上传私钥文件'
      ],
      evidence
    };
  }

  // 7. 通用诊断兜底
  return {
    engine: 'RULE_BASED',
    source: 'TincLink 通用规则排障引擎',
    summary: '已结合输入的故障描述与系统日志匹配 TincLink 综合诊断规则。建议依次排查服务链路、端口监听及安全策略。',
    confidence: 'MEDIUM',
    statusType: 'info',
    possibleCauses: [
      'Access Server 与 Node 间的 UDP 打洞或直接网络不可达',
      '两端 Tinc 配置文件中 Mode (router/switch) 或 Cipher 加密套件不匹配',
      '宿主机安全组或本地防火墙未放行虚拟网对应端口 (默认 655)',
      '系统日志中存在守护进程退出代码或配置解析警告'
    ],
    checks: [
      {
        title: '检查 Tinc 守护进程实时状态',
        description: '在接入端执行服务状态查询，重点查看最近退出码及 stderr。',
        command: 'systemctl status tinc --no-pager'
      },
      {
        title: '双向连通性测试 (Ping & 端口检测)',
        description: '确认网络层与传输层 UDP/TCP 可达。',
        command: 'nc -zvu <AccessServerIP> 655'
      },
      {
        title: '审查系统安全审计日志',
        description: '排查 SELinux 或 AppArmor 是否拦截虚拟网卡 tap 设备访问。',
        command: 'dmesg -T | grep -i tinc | tail -n 20'
      }
    ],
    warnings: [
      '排查过程中若需重启网络服务，请注意评估对业务流量的影响',
      '排障建议仅供管理员参考，禁止在生产环境未经审查直接批量执行命令'
    ],
    evidence
  };
}

/**
 * 结构化追问处理 (Follow-up)
 */
export function followUpDiagnose(previousResult, queryText) {
  const query = (queryText || '').toLowerCase();
  
  if (query.includes('端口') || query.includes('port') || query.includes('655') || query.includes('监听')) {
    return {
      title: '端口排查后续指引',
      answer: '若 655 端口监听正常，表明守护进程已在运行。下一步请重点排查：1. 云厂商安全组与系统防火墙（ufw/iptables）是否放行了 UDP 流量（Tinc 数据面主要依赖 UDP 传输）；2. 双向 hosts 公钥文件是否一致。',
      suggestedCommand: 'iptables -S INPUT | grep 655'
    };
  }
  
  if (query.includes('路由') || query.includes('route') || query.includes('ip route') || query.includes('网关')) {
    return {
      title: '路由与子网转发后续指引',
      answer: '请检查客户端本地路由表是否存在针对目标网段的指向 tinc 适配器的路由规则，并核查接入服务端是否开启了 IP 转发（sysctl net.ipv4.ip_forward 应为 1）。',
      suggestedCommand: 'ip route show | grep 10.'
    };
  }
  
  if (query.includes('证书') || query.includes('公钥') || query.includes('key') || query.includes('rsa') || query.includes('ed25519')) {
    return {
      title: '密钥认证后续指引',
      answer: '请确认服务端的 /etc/tinc/<net>/hosts/<client> 文件内容与客户端生成的公钥严格匹配。若修改过公钥，需在服务端执行 systemctl reload tinc@<net> 重新加载。',
      suggestedCommand: 'systemctl reload tinc@<NetworkName>'
    };
  }
  
  return {
    title: '专家诊断后续建议',
    answer: `针对您的追问 "${queryText}"：建议在接入服务器执行详细调试输出模式排查，或检查节点日志中的 Handshake 报错信息。`,
    suggestedCommand: 'tincd -n <NetworkName> -D -d 5'
  };
}

export default {
  diagnoseIssue,
  getAiOpsQuickTemplates,
  ruleBasedDiagnose,
  followUpDiagnose
}

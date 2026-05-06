import request from '@/utils/request'

// 获取全局网络状态统计数据
export function getGlobalNetworkStats() {
  return request({
    url: '/monitor/network/global-stats',
    method: 'get'
  })
}

// 获取单个内网的详细监控数据
export function getSingleNetworkMonitor(networkId) {
  return request({
    url: '/monitor/network/single-stats/' + networkId,
    method: 'get'
  })
}

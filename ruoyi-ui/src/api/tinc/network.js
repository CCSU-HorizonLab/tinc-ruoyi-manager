import request from '@/utils/request'

export function listNetwork(query) {
  return request({
    url: '/tinc/network/list',
    method: 'get',
    params: query
  })
}

export function getNetwork(id) {
  return request({
    url: '/tinc/network/' + id,
    method: 'get'
  })
}

// 查询网络真实运行状态（systemd、接口、地址及 TCP/UDP 监听）
export function getNetworkRuntime(networkId) {
  return request({
    url: '/tinc/network/runtime/' + networkId,
    method: 'get'
  })
}

export function addNetwork(data) {
  return request({
    url: '/tinc/network',
    method: 'post',
    data: data
  })
}

export function updateNetwork(data) {
  return request({
    url: '/tinc/network',
    method: 'put',
    data: data
  })
}

export function delNetwork(id) {
  return request({
    url: '/tinc/network/' + id,
    method: 'delete'
  })
}

import request from '@/utils/request'

export function listNode(query) {
  return request({
    url: '/tinc/node/list',
    method: 'get',
    params: query
  })
}

export function getNode(id) {
  return request({
    url: '/tinc/node/' + id,
    method: 'get'
  })
}

export function addNode(data) {
  return request({
    url: '/tinc/node',
    method: 'post',
    data: data
  })
}

export function updateNode(data) {
  return request({
    url: '/tinc/node',
    method: 'put',
    data: data
  })
}

export function delNode(id) {
  return request({
    url: '/tinc/node/' + id,
    method: 'delete'
  })
}

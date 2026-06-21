import request from '@/utils/request'

export function listServer(query) {
  return request({
    url: '/tinc/server/list',
    method: 'get',
    params: query
  })
}

export function getServer(id) {
  return request({
    url: '/tinc/server/' + id,
    method: 'get'
  })
}

export function addServer(data) {
  return request({
    url: '/tinc/server',
    method: 'post',
    data: data
  })
}

export function updateServer(data) {
  return request({
    url: '/tinc/server',
    method: 'put',
    data: data
  })
}

export function delServer(id) {
  return request({
    url: '/tinc/server/' + id,
    method: 'delete'
  })
}

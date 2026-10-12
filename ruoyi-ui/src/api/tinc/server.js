import request from '@/utils/request'

// Includes parent labels of explicitly granted networks, without exposing server connection settings.
export function listServerOptions() {
  return request({ url: '/tinc/server/options', method: 'get' })
}

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

export function probeServer(id) {
  return request({
    url: '/tinc/server/' + id + '/agent/probe',
    method: 'post'
  })
}

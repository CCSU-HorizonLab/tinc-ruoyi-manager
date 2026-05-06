import request from '@/utils/request'

/**
 * 查询服务器集群管理列表
 * @param {Object} query - 查询条件
 * @returns {Promise} - 服务器集群管理列表
 */
export function listManger(query) {
  return request({
    url: '/manger/mangeServer/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询服务器集群管理详细
 * @param {Number} Id - 服务器集群ID
 * @returns {Promise} - 服务器集群管理详情
 */
export function getManger(Id) {
  return request({
    url: '/manger/mangeServer/' + Id,
    method: 'get'
  })
}

/**
 * 新增服务器集群管理
 * @param {Object} data - 服务器集群管理数据
 * @returns {Promise} - 新增结果
 */
export function addManger(data) {
  return request({
    url: '/manger/mangeServer',
    method: 'post',
    data: data
  })
}

/**
 * 修改服务器集群管理
 * @param {Object} data - 服务器集群管理数据
 * @returns {Promise} - 修改结果
 */
export function updateManger(data) {
return request({
    url: '/manger/mangeServer',
    method: 'put',
    data: data
  })
}

/**
 * 删除服务器集群管理
 * @param {Number} Id - 服务器集群ID
 * @returns {Promise} - 删除结果
 */
export function delManger(Id) {
  return request({
    url: '/manger/mangeServer/' + Id,
    method: 'delete'
  })
}

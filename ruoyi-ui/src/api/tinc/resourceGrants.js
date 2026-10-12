import request from '@/utils/request'

export const getResourceGrantOptions = () => request({ url: '/tinc/resource-grants/options', method: 'get' })
export const getResourceGrants = userId => request({ url: '/tinc/resource-grants/' + userId, method: 'get' })
export const saveResourceGrants = (userId, data) => request({ url: '/tinc/resource-grants/' + userId, method: 'put', data })

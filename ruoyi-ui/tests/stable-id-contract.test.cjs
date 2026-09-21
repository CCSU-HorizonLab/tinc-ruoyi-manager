const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')

test('runtime status API and active network page use stable network IDs', () => {
  const api = read('src/api/tinc/network.js')
  const view = read('src/views/tinc/network/index.vue')

  assert.match(api, /getNetworkRuntime\(networkId\)/)
  assert.match(api, /url:\s*["']\/tinc\/network\/runtime\/["']\s*\+\s*networkId/)
  assert.match(view, /loadRuntimeStatus\(scope\.row\.id\)/)
  assert.match(view, /loadRuntimeStatus\(network\.id, false\)/)
  assert.doesNotMatch(view, /loadRuntimeStatus\(scope\.row\.networkName\)/)
})

test('runtime query failures are not presented as a real NOT_READY result', () => {
  const view = read('src/views/tinc/network/index.vue')

  assert.match(view, /readiness:\s*'UNKNOWN'/)
  assert.match(view, /failureCode:\s*'STATUS_QUERY_FAILED'/)
  assert.doesNotMatch(view, /error\.response\?\.data\?\.code\s*\|\|\s*'TINC_RUNTIME_NOT_READY'/)
})

test('active selectors display names but submit stable IDs', () => {
  const networkView = read('src/views/tinc/network/index.vue')
  const nodeView = read('src/views/tinc/node/index.vue')

  assert.match(networkView, /:label="server\.serverName"/)
  assert.match(networkView, /:value="server\.id"/)
  assert.match(nodeView, /:label="server\.serverName"/)
  assert.match(nodeView, /:value="server\.id"/)
  assert.match(nodeView, /:label="network\.networkName"/)
  assert.match(nodeView, /:value="network\.id"/)
})

test('name fields remain available only for display and search', () => {
  const networkView = read('src/views/tinc/network/index.vue')
  const nodeView = read('src/views/tinc/node/index.vue')

  assert.match(networkView, /networkName:\s*null/)
  assert.match(nodeView, /nodeName:\s*null/)
  assert.match(nodeView, /networkName:\s*null/)
})

test('application routes use the stable-ID management pages', () => {
  const router = read('src/router/index.js')

  assert.match(router, /path:\s*'\/tinc-network'[\s\S]*import\('@\/views\/tinc\/network\/index'\)/)
  assert.match(router, /path:\s*'\/tinc-server'[\s\S]*import\('@\/views\/tinc\/server\/index'\)/)
  assert.match(router, /path:\s*'\/tinc-node'[\s\S]*import\('@\/views\/tinc\/node\/index'\)/)
})

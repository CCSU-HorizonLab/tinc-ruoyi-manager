const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

// Execute the real Vue component methods with controlled HTTP responses; no network or DOM needed.
function component(file, api) {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/dashboard', file), 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '').replace('export default', 'module.exports =')
  const sandbox = { module: { exports: {} }, ...api, setInterval, clearInterval }
  vm.runInNewContext(script, sandbox)
  const definition = sandbox.module.exports
  const instance = definition.data()
  for (const [name, method] of Object.entries(definition.methods)) instance[name] = method.bind(instance)
  return { instance, definition, source }
}

test('global HTTP failure clears previous success instead of showing fake or stale READY', async () => {
  const { instance: view } = component('NetworkStatus.vue', {
    getGlobalNetworkStats: () => Promise.reject(new Error('offline'))
  })
  view.realAssets.readyNetworks = 5
  await view.getAllStats()
  assert.equal(view.realAssets.readyNetworks, null)
  assert.equal(view.realAssets.onlineRate, null)
  assert.match(view.queryError, /STATUS_QUERY_FAILED/)
  assert.equal(view.observations.length, 0)
})

test('global zero ready stays zero and overlapping refresh is suppressed', async () => {
  let calls = 0
  const { instance: view } = component('NetworkStatus.vue', {
    getGlobalNetworkStats: async () => { calls++; return { data: {
      networks: [], totalNetworks: 5, onlineNetworks: 0, onlineRate: 0,
      nodeSummary: { total: 2, configured: 1 }
    } } }
  })
  await Promise.all([view.getAllStats(), view.getAllStats()])
  assert.equal(calls, 1)
  assert.equal(view.realAssets.readyNetworks, 0)
  assert.equal(view.realAssets.onlineRate, 0)
})

test('single network HTTP failure is UNKNOWN, not simulated success or real NOT_READY', async () => {
  const { instance: view, definition } = component('SingleNetWorkControl.vue', {
    getSingleNetworkMonitor: async id => { assert.equal(id, 11); throw new Error('offline') }
  })
  view.selectedNetwork = true; view.selectedNetworkId = 11; view.readiness = 'READY'
  await view.getMonitorData()
  assert.equal(view.readiness, 'UNKNOWN')
  assert.equal(view.failureCode, 'STATUS_QUERY_FAILED')
  assert.equal(view.nodeSummary.total, null)
  assert.match(definition.computed.statusLabel.call(view), /UNKNOWN/)
})

test('single network uses stable selectors and ignores old network response after reset', async () => {
  let finish
  const { instance: view } = component('SingleNetWorkControl.vue', {
    listServerOptions: async () => ({ rows: [{ id: 4, serverName: 'Access-A' }] }),
    listNetwork: async query => {
      assert.equal(query.serverId, 4)
      assert.equal(query.serverName, undefined)
      return { rows: [{ id: 11, networkName: 'DEV_A' }] }
    },
    getSingleNetworkMonitor: () => new Promise(resolve => { finish = resolve })
  })
  await view.getServerOptions()
  assert.equal(view.servers[0].value, 4)
  view.selectedServer = 4
  await view.getNetworksByServer(4)
  assert.equal(view.networks[0].value, 11)
  view.selectedNetwork = true; view.selectedNetworkId = 11
  const pending = view.getMonitorData()
  view.resetSelection()
  finish({ data: { networkId: 11, readiness: 'READY', nodeSummary: { total: 1 } } })
  await pending
  assert.equal(view.readiness, 'UNKNOWN')
  assert.equal(view.loading, false)
})

test('real interface counters require two valid samples for a rate and never fake missing data', async () => {
  const samples = [
    { interfaceRxBytes: 1000, interfaceTxBytes: 0, trafficSampledAt: 1000 },
    { interfaceRxBytes: 1250, interfaceTxBytes: 2000, trafficSampledAt: 3000 },
    { interfaceRxBytes: null, interfaceTxBytes: null, trafficSampledAt: null }
  ]
  const { instance: view } = component('SingleNetWorkControl.vue', {
    getSingleNetworkMonitor: async () => ({ data: {
      networkId: 11, readiness: 'READY', nodeSummary: { total: 2, configured: 2 },
      statusProbeMs: 0, interfaceName: 'tinc0', trafficSource: 'LINUX_INTERFACE_COUNTERS',
      ...samples.shift()
    } })
  })
  view.selectedNetwork = true; view.selectedNetworkId = 11
  await view.getMonitorData()
  assert.equal(view.interfaceRxBytes, 1000)
  assert.equal(view.interfaceTxBytes, 0)
  assert.equal(view.statusProbeMs, 0)
  assert.equal(view.rxRateBps, null)
  await view.getMonitorData()
  assert.equal(view.rxRateBps, 1000)
  assert.equal(view.txRateBps, 8000)
  await view.getMonitorData()
  assert.equal(view.interfaceRxBytes, null)
  assert.equal(view.rxRateBps, null)
  assert.equal(view.previousTrafficSample, null)
})

test('stale samples and counter resets never appear as a current traffic rate', async () => {
  const samples = [
    { interfaceRxBytes: 1000, interfaceTxBytes: 1000, trafficSampledAt: 1000 },
    { interfaceRxBytes: 9000, interfaceTxBytes: 9000, trafficSampledAt: 301000 },
    { interfaceRxBytes: 9250, interfaceTxBytes: 9250, trafficSampledAt: 303000 },
    { interfaceRxBytes: 5, interfaceTxBytes: 5, trafficSampledAt: 305000 }
  ]
  const { instance: view } = component('SingleNetWorkControl.vue', {
    getSingleNetworkMonitor: async () => ({ data: {
      networkId: 11, readiness: 'READY', nodeSummary: { total: 1, configured: 1 },
      interfaceName: 'tinc0', trafficSource: 'LINUX_INTERFACE_COUNTERS', ...samples.shift()
    } })
  })
  view.selectedNetwork = true; view.selectedNetworkId = 11
  await view.getMonitorData()
  await view.getMonitorData()
  assert.equal(view.rxRateBps, null)
  assert.equal(view.interfaceRxBytes, 9000)
  await view.getMonitorData()
  assert.equal(view.rxRateBps, 1000)
  await view.getMonitorData()
  assert.equal(view.rxRateBps, null)
  assert.equal(view.interfaceRxBytes, 5)
})

test('monitor views contain no synthetic charts or success fallbacks', () => {
  for (const file of ['SingleNetWorkControl.vue', 'NetworkStatus.vue']) {
    const { source } = component(file, {})
    assert.doesNotMatch(source, /simulateMonitorData|simulateDataUpdate|Math\.random|<bar-chart|<line-chart/)
    assert.match(source, /未采集/)
  }
})

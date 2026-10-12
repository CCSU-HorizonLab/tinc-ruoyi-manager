const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const read = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8')

function dialog(api) {
  const script = read('src/components/TincResourceGrantDialog/index.vue').match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '').replace('export default', 'module.exports =')
  const context = { module: { exports: {} }, ...api }
  vm.runInNewContext(script, context)
  const definition = context.module.exports
  const view = definition.data()
  for (const [name, method] of Object.entries(definition.methods)) view[name] = method.bind(view)
  view.$confirm = async () => {}
  view.$message = { success() {} }
  return view
}

test('failed grant loading cannot be saved as accidental revoke-all', async () => {
  let saved = false
  const view = dialog({ getResourceGrants: async () => { throw new Error('offline') },
    getResourceGrantOptions: async () => ({ data: { servers: [], networks: [] } }),
    saveResourceGrants: async () => { saved = true } })
  await view.open({ userId: 20, userName: 'ordinary' })
  await view.save()
  assert.equal(view.loaded, false)
  assert.equal(saved, false)
})

test('save sends stable IDs and cancellation makes no request', async () => {
  const calls = []
  const view = dialog({ getResourceGrants: async () => ({ data: { serverIds: [3], networkIds: [8] } }),
    getResourceGrantOptions: async () => ({ data: { servers: [], networks: [] } }),
    saveResourceGrants: async (id, data) => { calls.push({ id, data: JSON.parse(JSON.stringify(data)) }) } })
  await view.open({ userId: 20, userName: 'ordinary' })
  view.$confirm = async () => { throw new Error('cancel') }
  await view.save()
  assert.equal(calls.length, 0)
  view.$confirm = async () => {}
  await view.save()
  assert.deepEqual(calls, [{ id: 20, data: { serverIds: [3], networkIds: [8] } }])
})

test('stale response for a previously selected user cannot overwrite current grants', async () => {
  let finish
  const view = dialog({ getResourceGrants: id => id === 20 ? new Promise(resolve => { finish = resolve })
    : Promise.resolve({ data: { serverIds: [4], networkIds: [] } }),
    getResourceGrantOptions: async () => ({ data: { servers: [], networks: [] } }) })
  const pending = view.open({ userId: 20, userName: 'old' })
  await view.open({ userId: 21, userName: 'new' })
  finish({ data: { serverIds: [3], networkIds: [] } })
  await pending
  assert.equal(view.userId, 21)
  assert.equal(view.form.serverIds[0], 4)
})

test('resource editor is limited to super-admin and network selectors use minimal parent options', () => {
  assert.match(read('src/views/system/user/index.vue'), /Number\(\$store\.getters\.id\) === 1/)
  for (const file of ['src/views/tinc/node/index.vue', 'src/views/tinc/network/index.vue',
    'src/views/dashboard/SingleNetWorkControl.vue']) {
    assert.match(read(file), /listServerOptions\(\)/)
  }
  const source = read('src/components/TincResourceGrantDialog/index.vue')
  assert.match(source, /:value="server\.id"/)
  assert.match(source, /:value="network\.id"/)
  assert.doesNotMatch(source, /:value="(?:server\.serverName|network\.networkName)"/)
})

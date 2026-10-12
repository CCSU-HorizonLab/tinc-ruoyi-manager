<template>
  <el-dialog :title="'网络资源授权 · ' + userName" :visible.sync="visible" width="650px" append-to-body :close-on-click-modal="false">
    <el-alert type="info" :closable="false" show-icon title="资源范围与角色功能权限同时生效。服务器授权覆盖其全部现有及后续网络/节点；网络授权不包含同服务器的其他网络。" />
    <el-form v-loading="loading" label-width="100px" style="margin-top: 20px;">
      <el-form-item label="服务器范围">
        <el-select v-model="form.serverIds" multiple filterable :disabled="loading || saving" style="width: 100%;" placeholder="未选择即不授权整台服务器">
          <el-option v-for="server in servers" :key="server.id" :value="server.id" :label="server.serverName + ' (#' + server.id + ')'" />
        </el-select>
      </el-form-item>
      <el-form-item label="指定网络">
        <el-select v-model="form.networkIds" multiple filterable :disabled="loading || saving" style="width: 100%;" placeholder="可单独授权网络及其节点">
          <el-option v-for="network in networks" :key="network.id" :value="network.id" :label="network.serverName + ' / ' + network.networkName + ' (#' + network.id + ')'" />
        </el-select>
      </el-form-item>
      <p>只授权网络时，可在选择器看到所属服务器名称，但不能管理该服务器。两项全部清空会撤销此账号所有 Tinc 管理资源权限，不停止 VPN 或撤销节点客户端 Token。</p>
      <p>服务器/网络删除后不能通过名字继承旧授权。授权不允许普通账号再次转授他人。</p>
    </el-form>
    <div slot="footer">
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="loading || !loaded" :loading="saving" @click="save">保存授权</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getResourceGrants, getResourceGrantOptions, saveResourceGrants } from '@/api/tinc/resourceGrants'

export default {
  name: 'TincResourceGrantDialog',
  data() {
    return { visible: false, loading: false, loaded: false, saving: false, version: 0,
      userId: null, userName: '', servers: [], networks: [], form: { serverIds: [], networkIds: [] } }
  },
  methods: {
    async open(user) {
      if (this.saving) return
      const version = ++this.version
      this.userId = user.userId
      this.userName = user.userName
      this.form = { serverIds: [], networkIds: [] }
      this.loaded = false
      this.visible = true
      this.loading = true
      try {
        const [grants, options] = await Promise.all([getResourceGrants(user.userId), getResourceGrantOptions()])
        if (version !== this.version) return
        this.form = { serverIds: grants.data.serverIds, networkIds: grants.data.networkIds }
        this.servers = options.data.servers
        this.networks = options.data.networks
        this.loaded = true
      } catch (_) {
        if (version === this.version) this.loaded = false
      } finally {
        if (version === this.version) this.loading = false
      }
    },
    async save() {
      if (!this.loaded || this.loading || this.saving) return
      this.saving = true
      try {
        await this.$confirm('确认替换此账号的全部资源授权？后续管理请求立即按新范围检查。', '确认授权', { type: 'warning' })
        await saveResourceGrants(this.userId, { serverIds: [...this.form.serverIds], networkIds: [...this.form.networkIds] })
        this.$message.success('资源授权已保存')
        this.visible = false
      } catch (_) {
        // HTTP errors are presented by the shared request layer; cancellation changes nothing.
      } finally { this.saving = false }
    }
  }
}
</script>

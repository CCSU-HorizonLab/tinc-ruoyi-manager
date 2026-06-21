<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="服务器" prop="serverName">
        <el-input
          v-model="queryParams.serverName"
          placeholder="请输入服务器"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内网名称" prop="networkName">
        <el-input
          v-model="queryParams.networkName"
          placeholder="请输入内网名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="节点数量" prop="nodes">
        <el-input
          v-model="queryParams.nodes"
          placeholder="请输入节点数量"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['TincNetworkMange:TincNetworkMange:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="networkList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ids" align="center" prop="id" />
      <el-table-column label="用户" align="center" prop="rootName" />
      <el-table-column label="接入服务器" align="center" prop="serverName" />
      <el-table-column label="内网名称" align="center" prop="networkName" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="网段" align="center" prop="segment" />
      <el-table-column label="节点数量" align="center" prop="nodes" />
      <el-table-column label="内网状态" align="center" prop="networkStatus" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['TincNetworkMange:TincNetworkMange:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['TincNetworkMange:TincNetworkMange:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改内网管理对话框 -->
  <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
  <el-form ref="form" :model="form" :rules="rules" label-width="80px">
    <el-form-item label="接入服务器" prop="serverName">
       <el-select v-model="form.serverName" placeholder="请选择接入服务器" @change="onServerChange">
        <el-option v-for="server in serverOptions" :key="server.serverName" :label="server.serverName" :value="server.serverName"></el-option>
      </el-select>
    </el-form-item>
    <el-form-item label="内网名称" prop="networkName">
      <el-input v-model="form.networkName" placeholder="请输入内网名称" />
    </el-form-item>
   <el-form-item label="网段" prop="segment">
       <el-select v-model="form.segment" placeholder="请选择网段" :disabled="!currentServer">
        <el-option v-for="segment in segmentOptions" :key="segment.value" :label="segment.label" :value="segment.value"></el-option>
      </el-select>
   </el-form-item>
    <el-form-item label="端口" prop="port">
       <el-select v-model="form.port" placeholder="请选择端口" :disabled="!currentServer">
        <el-option v-for="port in portOptions" :key="port.value" :label="port.label" :value="port.value"></el-option>
      </el-select>
    </el-form-item>
    <el-form-item label="备注" prop="explanation">
      <el-input v-model="form.explanation" placeholder="请输入备注" />
    </el-form-item>
  </el-form>
  <div slot="footer" class="dialog-footer">
    <el-button type="primary" @click="submitForm" :loading="isSubmitting">确 定</el-button>
    <el-button @click="cancel">取 消</el-button>
  </div>
</el-dialog>
  </div>
</template>

<script>
import { listNetwork, getNetwork, delNetwork, addNetwork, updateNetwork } from "@/api/tinc/network"
import { listServer } from "@/api/tinc/server"

export default {
  name: "Network",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      networkList: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        rootName: null,
        serverName: null,
        networkName: null,
        segment: null,
        nodes: null,
        networkStatus: null,
      },
      form: {},
      rules: {
        serverName: [
          { required: true, message: "接入服务器不能为空", trigger: "blur" }
        ],
        networkName: [
          { required: true, message: "内网名称不能为空", trigger: "blur" }
        ],
        createTime: [
          { required: true, message: "创建时间不能为空", trigger: "blur" }
        ],
        port: [
          { required: true, message: "端口不能为空", trigger: "change" },
          { validator: this.validatePort, trigger: "change" }
        ],
        segment: [
          { required: true, message: "网段不能为空", trigger: "change" },
          { validator: this.validateSegment, trigger: "change" }
        ],
        nodes: [
          { required: true, message: "节点数量不能为空", trigger: "blur" }
        ],
        networkStatus: [
          { required: true, message: "内网状态不能为空", trigger: "change" }
        ],
      },
      isSubmitting: false,
      serverOptions: [],
      currentServer: null,
      segmentOptions: [],
      portOptions: []
    }
  },
  created() {
    this.getList()
    this.getServerOptions();
  },
  activated() {
    this.getServerOptions();
  },
  methods: {
    getList() {
      this.loading = true
      listNetwork(this.queryParams).then(response => {
        this.networkList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: null,
        rootName: null,
        serverName: null,
        networkName: null,
        createTime: null,
        port: null,
        segment: null,
        nodes: null,
        networkStatus: '在线',
        explanation: null
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.form.rootName = this.$store.getters.name;
      this.currentServer = null;
      this.segmentOptions = [];
      this.portOptions = [];
      this.form.serverName = '';
      this.open = true
      this.title = "添加内网"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getNetwork(id).then(response => {
        this.form = response.data
        const savedPort = this.form.port;
        const savedSegment = this.form.segment;
        this.onServerChange(this.form.serverName);
        this.form.port = savedPort;
        this.form.segment = savedSegment;
        this.open = true
        this.title = "修改内网"
      })
    },
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      this.form.rootName = this.$store.getters.name;

      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateNetwork(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).catch(error => {
              this.$modal.msgError(`修改失败: ${error.response?.data?.msg || error.message}`)
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addNetwork(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            }).catch(error => {
              this.$modal.msgError(`新增失败: ${error.response?.data?.msg || error.message}`)
            }).finally(() => {
              this.isSubmitting = false;
            })
          }
        } else {
          this.isSubmitting = false;
        }
      })
    },
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('是否确认删除内网编号为"' + ids + '"的数据项？').then(function() {
        return delNetwork(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('tinc/network/export', {
        ...this.queryParams
      }, `network_${new Date().getTime()}.xlsx`)
    },

    getServerOptions(){
      listServer({ pageNum: 1, pageSize: 10000 }).then(response => {
        this.serverOptions = response.rows;
      }).catch(error => {
        console.error('获取服务器列表失败:', error);
        this.$modal.msgError('获取服务器列表失败');
      });
    },

    onServerChange(serverName){
      const server = this.serverOptions.find(s => s.serverName === serverName);
      this.currentServer = server;

      this.form.port = '';
      this.form.segment = '';

      if(server){
        try {
          this.segmentOptions = this.generateSegmentOptions(server.startInterat, server.endInterat);
          this.portOptions = this.generatePortOptions(server.startPort, server.endPort);
        } catch (error) {
          console.error('生成选项失败:', error);
          this.segmentOptions = [];
          this.portOptions = [];
        }
      }else{
        this.segmentOptions = [];
        this.portOptions = [];
      }
    },

    generateSegmentOptions(startInterat, endInterat) {
      const segments = [];
      if (!startInterat || !endInterat) return segments;

      try {
        const startParts = startInterat.split('.');
        const endParts = endInterat.split('.');

        if (startParts.length === 3 && endParts.length === 3 &&
            startParts[0] === endParts[0] && startParts[1] === endParts[1]) {

          const start = parseInt(startParts[2]);
          const end = parseInt(endParts[2]);

          if (!isNaN(start) && !isNaN(end) && start <= end) {
            for (let i = start; i <= end; i++) {
              segments.push({
                label: `${startParts[0]}.${startParts[1]}.${i}`,
                value: `${startParts[0]}.${startParts[1]}.${i}`
              });
            }
          }
        }
      } catch (error) {
        console.error('生成网段选项失败:', error);
      }

      return segments;
    },

    generatePortOptions(startPort, endPort) {
      const ports = [];
      if (!startPort || !endPort) return ports;

      try {
        const start = parseInt(startPort);
        const end = parseInt(endPort);

        if (!isNaN(start) && !isNaN(end) && start <= end) {
          const maxPorts = 100;
          const actualEnd = Math.min(end, start + maxPorts);

          for (let i = start; i <= actualEnd; i++) {
            ports.push({
              label: `${i}`,
              value: `${i}`
            });
          }
        }
      } catch (error) {
        console.error('生成端口选项失败:', error);
      }

      return ports;
    },

    validatePort(rule, value, callback) {
      if (!value) {
        return callback(new Error('端口不能为空'));
      }

      if (this.currentServer) {
        const port = parseInt(value);
        const startPort = parseInt(this.currentServer.startPort);
        const endPort = parseInt(this.currentServer.endPort);

        if (isNaN(port) || port < startPort || port > endPort) {
          return callback(new Error(`端口必须在 ${startPort} 到 ${endPort} 之间`));
        }
      }

      callback();
    },

    validateSegment(rule, value, callback) {
      if (!value) {
        return callback(new Error('网段不能为空'));
      }

      if (this.currentServer) {
        const startInterat = this.currentServer.startInterat;
        const endInterat = this.currentServer.endInterat;

        const startParts = startInterat.split('.');
        const endParts = endInterat.split('.');
        const valueParts = value.split('.');

        if (startParts.length === 3 && endParts.length === 3 && valueParts.length === 3) {
          if (startParts[0] === endParts[0] && startParts[0] === valueParts[0] &&
              startParts[1] === endParts[1] && startParts[1] === valueParts[1]) {

            const start = parseInt(startParts[2]);
            const end = parseInt(endParts[2]);
            const valueNum = parseInt(valueParts[2]);

            if (isNaN(valueNum) || valueNum < start || valueNum > end) {
              return callback(new Error(`网段必须在 ${startInterat} 到 ${endInterat} 之间`));
            }
          }
        }
      }

      callback();
    }
  }
}
</script>

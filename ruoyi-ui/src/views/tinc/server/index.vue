<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="服务器名字" prop="serverName">
        <el-input
          v-model="queryParams.serverName"
          placeholder="请输入服务器名字"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="服务器ip" prop="serverIp">
        <el-input
          v-model="queryParams.serverIp"
          placeholder="请输入服务器ip"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内网数量" prop="number">
        <el-input
          v-model="queryParams.number"
          placeholder="请输入内网数量"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="dict in dict.type.server_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
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
          v-hasPermi="['manger:manger:add']"
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
          v-hasPermi="['manger:manger:edit']"
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
          v-hasPermi="['manger:manger:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['manger:manger:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="serverList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="id" align="center" prop="id" />
      <el-table-column label="服务器名字" align="center" prop="serverName" />
      <el-table-column label="服务器ip" align="center" prop="serverIp" />
      <el-table-column label="内网数量" align="center" prop="number" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status == '1' ? 'success' : 'info'">
            {{ scope.row.status == '1' ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['manger:manger:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['manger:manger:remove']"
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

    <!-- 添加或修改服务器管理对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="服务器名字" prop="serverName">
          <el-input v-model="form.serverName" placeholder="请输入服务器名字" />
        </el-form-item>
        <el-form-item label="服务器ip" prop="serverIp">
          <el-input v-model="form.serverIp" placeholder="请输入服务器ip" />
        </el-form-item>
        <el-form-item label="起始网段" prop="startInterat">
          <el-input v-model="form.startInterat" placeholder="请输入起始网段" />
        </el-form-item>
        <el-form-item label="终止网段" prop="endInterat">
          <el-input v-model="form.endInterat" placeholder="请输入终止网段" />
        </el-form-item>
        <el-form-item label="起始端口" prop="startPort">
          <el-input v-model="form.startPort" placeholder="请输入起始端口" />
        </el-form-item>
        <el-form-item label="终止端口" prop="endPort">
          <el-input v-model="form.endPort" placeholder="请输入终止端口" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
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
import { listServer, getServer, delServer, addServer, updateServer } from "@/api/tinc/server"

export default {
  name: "Server",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      serverList: [],
      dict: {
        type: {
          server_status: [
            { value: '0', label: '离线' },
            { value: '1', label: '在线' }
          ]
        }
      },
      title: "",
      isSubmitting: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        serverName: null,
        serverIp: null,
        number: null,
        status: null
      },
      form: {},
      rules: {
        serverName: [
          { required: true, message: "服务器名字不能为空", trigger: "blur" }
        ],
        serverIp: [
          { required: true, message: "服务器ip不能为空", trigger: "blur" }
        ],
        startInterat: [
          { required: true, message: "起始网段不能为空", trigger: "blur" }
        ],
        endInterat: [
          { required: true, message: "终止网段不能为空", trigger: "blur" }
        ],
        startPort: [
          { required: true, message: "起始端口不能为空", trigger: "blur" }
        ],
        endPort: [
          { required: true, message: "终止端口不能为空", trigger: "blur" }
        ],
        number: [
          { required: true, message: "内网数量不能为空", trigger: "blur" }
        ],
        status: [
          { required: true, message: "状态不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listServer(this.queryParams).then(response => {
        this.serverList = response.rows
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
        serverName: null,
        serverIp: null,
        startInterat: null,
        endInterat: null,
        startPort: null,
        endPort: null,
        remark: null,
        number: null,
        status: null
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
      this.open = true
      this.title = "添加服务器"
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids
      getServer(id).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改服务器"
      })
    },
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateServer(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addServer(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
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
      this.$modal.confirm('是否确认删除服务器编号为"' + ids + '"的数据项？').then(function() {
        return delServer(ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('tinc/server/export', {
        ...this.queryParams
      }, `server_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>

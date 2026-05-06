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

    <el-table v-loading="loading" :data="mangerList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="id" align="center" prop="id" /> <!-- 修改: 从prop="Id"改为prop="id"，与后端返回的JSON字段名保持一致 -->
      <el-table-column label="服务器名字" align="center" prop="serverName" />
      <el-table-column label="服务器ip" align="center" prop="serverIp" />
      <el-table-column label="内网数量" align="center" prop="number" />
      <el-table-column label="状态" align="center" prop="status"> <!-- 修改: 从prop="staust"改为prop="status"，修正拼写错误 -->
        <template slot-scope="scope">
          <dict-tag :options="dict.type.server_status" :value="scope.row.status"/>
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

    <!-- 添加或修改服务器集群管理对话框 -->
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
        <el-form-item label="起始端口" prop="startPost">
          <el-input v-model="form.startPost" placeholder="请输入起始端口" />
        </el-form-item>
        <el-form-item label="终止端口" prop="endPost">
          <el-input v-model="form.endPost" placeholder="请输入终止端口" />
        </el-form-item>
        <el-form-item label="备注" prop="election">
          <el-input v-model="form.election" placeholder="请输入备注" />
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
import { listManger, getManger, delManger, addManger, updateManger } from "@/api/manger/manger"

export default {
  name: "Manger",
  dicts: ['server_status'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 服务器集群管理表格数据
      mangerList: [],
      // 弹出层标题
      title: "",
      // 是否正在提交
      isSubmitting: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        serverName: null,
        serverIp: null,
        number: null,
        status: null // 修改: 从staust改为status，修正拼写错误
      },
      // 表单参数
      form: {},
      // 表单校验
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
        startPost: [
          { required: true, message: "起始端口不能为空", trigger: "blur" }
        ],
        endPost: [
          { required: true, message: "终止端口不能为空", trigger: "blur" }
        ],
        number: [
          { required: true, message: "内网数量不能为空", trigger: "blur" }
        ],
        status: [
          { required: true, message: "状态不能为空", trigger: "change" }
        ] // 修改: 从staust改为status，修正拼写错误
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询服务器集群管理列表 */
    getList() {
      this.loading = true
      listManger(this.queryParams).then(response => {
        this.mangerList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        id: null, // 修改: 从Id改为id，与后端返回的JSON字段名保持一致
        serverName: null,
        serverIp: null,
        startInterat: null,
        endInterat: null,
        startPost: null,
        endPost: null,
        election: null,
        number: null,
        status: null // 修改: 从staust改为status，修正拼写错误
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id) // 修改: 从item.Id改为item.id，与后端返回的JSON字段名保持一致
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加服务器集群管理"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const Id = row.id || this.ids // 修改: 从row.Id改为row.id，与后端返回的JSON字段名保持一致
      getManger(Id).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改服务器集群管理"
      })
    },
    /** 提交按钮 */
    submitForm() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) { // 修改: 从this.form.Id改为this.form.id，与后端返回的JSON字段名保持一致
            updateManger(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => {
              this.isSubmitting = false;
            })
          } else {
            addManger(this.form).then(response => {
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
    /** 删除按钮操作 */
    handleDelete(row) {
      const Ids = row.id || this.ids // 修改: 从row.Id改为row.id，与后端返回的JSON字段名保持一致
      this.$modal.confirm('是否确认删除服务器集群管理编号为"' + Ids + '"的数据项？').then(function() {
        return delManger(Ids)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('manger/manger/export', {
        ...this.queryParams
      }, `manger_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>

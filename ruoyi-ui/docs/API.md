# 项目 API 接口文档

本文档描述了项目中所有前端 API 接口的定义和用法。

---

## 目录

1. [认证模块](#1-认证模块)
2. [路由菜单](#2-路由菜单)
3. [业务模块](#3-业务模块)
   - [优惠券管理](#31-优惠券管理)
   - [顾客管理](#32-顾客管理)
   - [优惠券领取记录](#33-优惠券领取记录)
   - [核销记录](#34-核销记录)
4. [系统管理](#4-系统管理)
   - [用户管理](#41-用户管理)
   - [角色管理](#42-角色管理)
   - [菜单管理](#43-菜单管理)
   - [部门管理](#44-部门管理)
   - [岗位管理](#45-岗位管理)
   - [字典管理](#46-字典管理)
   - [参数配置](#47-参数配置)
   - [通知公告](#48-通知公告)
5. [系统监控](#5-系统监控)
   - [在线用户](#51-在线用户)
   - [定时任务](#52-定时任务)
   - [调度日志](#53-调度日志)
   - [登录日志](#54-登录日志)
   - [操作日志](#55-操作日志)
   - [缓存监控](#56-缓存监控)
   - [服务监控](#57-服务监控)
6. [系统工具](#6-系统工具)
   - [代码生成](#61-代码生成)
7. [管理员管理](#7-管理员管理)

---

## 1. 认证模块

文件位置: `src/api/login.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `login` | POST | `/login` | 用户登录 | `username`, `password`, `code`, `uuid` |
| `register` | POST | `/register` | 用户注册 | `data` (注册信息对象) |
| `getInfo` | GET | `/getInfo` | 获取用户详细信息 | - |
| `logout` | POST | `/logout` | 退出登录 | - |
| `getCodeImg` | GET | `/captchaImage` | 获取验证码图片 | - |


---

## 2. 路由菜单

文件位置: `src/api/menu.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `getRouters` | GET | `/getRouters` | 获取路由菜单 | - |

---

## 3. 业务模块

### 3.1 优惠券管理

文件位置: `src/api/coupons/coupons.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listCoupons` | GET | `/coupons/coupons/list` | 查询优惠券列表 | `query` (查询参数) |
| `getCoupons` | GET | `/coupons/coupons/{couponId}` | 查询优惠券详细 | `couponId` |
| `addCoupons` | POST | `/coupons/coupons` | 新增优惠券 | `data` |
| `updateCoupons` | PUT | `/coupons/coupons` | 修改优惠券 | `data` |
| `delCoupons` | DELETE | `/coupons/coupons/{couponId}` | 删除优惠券 | `couponId` |

### 3.2 顾客管理

文件位置: `src/api/customers/customers.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listCustomers` | GET | `/customers/customers/list` | 查询顾客列表 | `query` (查询参数) |
| `getCustomers` | GET | `/customers/customers/{customerId}` | 查询顾客详细 | `customerId` |
| `addCustomers` | POST | `/customers/customers` | 新增顾客 | `data` |
| `updateCustomers` | PUT | `/customers/customers` | 修改顾客 | `data` |
| `delCustomers` | DELETE | `/customers/customers/{customerId}` | 删除顾客 | `customerId` |

### 3.3 优惠券领取记录

文件位置: `src/api/receipts/receipts.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listReceipts` | GET | `/receipts/receipts/list` | 查询领取记录列表 | `query` (查询参数) |
| `getReceipts` | GET | `/receipts/receipts/{receiptId}` | 查询领取记录详细 | `receiptId` |
| `addReceipts` | POST | `/receipts/receipts` | 新增领取记录 | `data` |
| `updateReceipts` | PUT | `/receipts/receipts` | 修改领取记录 | `data` |
| `delReceipts` | DELETE | `/receipts/receipts/{receiptId}` | 删除领取记录 | `receiptId` |

### 3.4 核销记录

文件位置: `src/api/records/records.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listRecords` | GET | `/records/records/list` | 查询核销记录列表 | `query` (查询参数) |
| `getRecords` | GET | `/records/records/{recordId}` | 查询核销记录详细 | `recordId` |
| `addRecords` | POST | `/records/records` | 新增核销记录 | `data` |
| `updateRecords` | PUT | `/records/records` | 修改核销记录 | `data` |
| `delRecords` | DELETE | `/records/records/{recordId}` | 删除核销记录 | `recordId` |

---

## 4. 系统管理

### 4.1 用户管理

文件位置: `src/api/system/user.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listUser` | GET | `/system/user/list` | 查询用户列表 | `query` |
| `getUser` | GET | `/system/user/{userId}` | 查询用户详细 | `userId` |
| `addUser` | POST | `/system/user` | 新增用户 | `data` |
| `updateUser` | PUT | `/system/user` | 修改用户 | `data` |
| `delUser` | DELETE | `/system/user/{userId}` | 删除用户 | `userId` |
| `resetUserPwd` | PUT | `/system/user/resetPwd` | 重置用户密码 | `userId`, `password` |
| `changeUserStatus` | PUT | `/system/user/changeStatus` | 修改用户状态 | `userId`, `status` |
| `getUserProfile` | GET | `/system/user/profile` | 查询用户个人信息 | - |
| `updateUserProfile` | PUT | `/system/user/profile` | 修改用户个人信息 | `data` |
| `updateUserPwd` | PUT | `/system/user/profile/updatePwd` | 修改用户密码 | `oldPassword`, `newPassword` |
| `uploadAvatar` | POST | `/system/user/profile/avatar` | 上传用户头像 | `data` (FormData) |
| `getAuthRole` | GET | `/system/user/authRole/{userId}` | 查询授权角色 | `userId` |
| `updateAuthRole` | PUT | `/system/user/authRole` | 保存授权角色 | `data` |
| `deptTreeSelect` | GET | `/system/user/deptTree` | 查询部门下拉树 | - |

### 4.2 角色管理

文件位置: `src/api/system/role.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listRole` | GET | `/system/role/list` | 查询角色列表 | `query` |
| `getRole` | GET | `/system/role/{roleId}` | 查询角色详细 | `roleId` |
| `addRole` | POST | `/system/role` | 新增角色 | `data` |
| `updateRole` | PUT | `/system/role` | 修改角色 | `data` |
| `delRole` | DELETE | `/system/role/{roleId}` | 删除角色 | `roleId` |
| `dataScope` | PUT | `/system/role/dataScope` | 角色数据权限 | `data` |
| `changeRoleStatus` | PUT | `/system/role/changeStatus` | 修改角色状态 | `roleId`, `status` |
| `allocatedUserList` | GET | `/system/role/authUser/allocatedList` | 查询已授权用户列表 | `query` |
| `unallocatedUserList` | GET | `/system/role/authUser/unallocatedList` | 查询未授权用户列表 | `query` |
| `authUserCancel` | PUT | `/system/role/authUser/cancel` | 取消用户授权角色 | `data` |
| `authUserCancelAll` | PUT | `/system/role/authUser/cancelAll` | 批量取消用户授权 | `data` |
| `authUserSelectAll` | PUT | `/system/role/authUser/selectAll` | 批量授权用户 | `data` |
| `deptTreeSelect` | GET | `/system/role/deptTree/{roleId}` | 根据角色查询部门树 | `roleId` |

### 4.3 菜单管理

文件位置: `src/api/system/menu.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listMenu` | GET | `/system/menu/list` | 查询菜单列表 | `query` |
| `getMenu` | GET | `/system/menu/{menuId}` | 查询菜单详细 | `menuId` |
| `addMenu` | POST | `/system/menu` | 新增菜单 | `data` |
| `updateMenu` | PUT | `/system/menu` | 修改菜单 | `data` |
| `delMenu` | DELETE | `/system/menu/{menuId}` | 删除菜单 | `menuId` |
| `treeselect` | GET | `/system/menu/treeselect` | 查询菜单下拉树 | - |
| `roleMenuTreeselect` | GET | `/system/menu/roleMenuTreeselect/{roleId}` | 根据角色查询菜单树 | `roleId` |

### 4.4 部门管理

文件位置: `src/api/system/dept.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listDept` | GET | `/system/dept/list` | 查询部门列表 | `query` |
| `listDeptExcludeChild` | GET | `/system/dept/list/exclude/{deptId}` | 查询部门列表(排除节点) | `deptId` |
| `getDept` | GET | `/system/dept/{deptId}` | 查询部门详细 | `deptId` |
| `addDept` | POST | `/system/dept` | 新增部门 | `data` |
| `updateDept` | PUT | `/system/dept` | 修改部门 | `data` |
| `delDept` | DELETE | `/system/dept/{deptId}` | 删除部门 | `deptId` |

### 4.5 岗位管理

文件位置: `src/api/system/post.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listPost` | GET | `/system/post/list` | 查询岗位列表 | `query` |
| `getPost` | GET | `/system/post/{postId}` | 查询岗位详细 | `postId` |
| `addPost` | POST | `/system/post` | 新增岗位 | `data` |
| `updatePost` | PUT | `/system/post` | 修改岗位 | `data` |
| `delPost` | DELETE | `/system/post/{postId}` | 删除岗位 | `postId` |


### 4.6 字典管理

#### 字典类型

文件位置: `src/api/system/dict/type.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listType` | GET | `/system/dict/type/list` | 查询字典类型列表 | `query` |
| `getType` | GET | `/system/dict/type/{dictId}` | 查询字典类型详细 | `dictId` |
| `addType` | POST | `/system/dict/type` | 新增字典类型 | `data` |
| `updateType` | PUT | `/system/dict/type` | 修改字典类型 | `data` |
| `delType` | DELETE | `/system/dict/type/{dictId}` | 删除字典类型 | `dictId` |
| `refreshCache` | DELETE | `/system/dict/type/refreshCache` | 刷新字典缓存 | - |
| `optionselect` | GET | `/system/dict/type/optionselect` | 获取字典选择框列表 | - |

#### 字典数据

文件位置: `src/api/system/dict/data.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listData` | GET | `/system/dict/data/list` | 查询字典数据列表 | `query` |
| `getData` | GET | `/system/dict/data/{dictCode}` | 查询字典数据详细 | `dictCode` |
| `getDicts` | GET | `/system/dict/data/type/{dictType}` | 根据字典类型查询数据 | `dictType` |
| `addData` | POST | `/system/dict/data` | 新增字典数据 | `data` |
| `updateData` | PUT | `/system/dict/data` | 修改字典数据 | `data` |
| `delData` | DELETE | `/system/dict/data/{dictCode}` | 删除字典数据 | `dictCode` |

### 4.7 参数配置

文件位置: `src/api/system/config.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listConfig` | GET | `/system/config/list` | 查询参数列表 | `query` |
| `getConfig` | GET | `/system/config/{configId}` | 查询参数详细 | `configId` |
| `getConfigKey` | GET | `/system/config/configKey/{configKey}` | 根据键名查询参数值 | `configKey` |
| `addConfig` | POST | `/system/config` | 新增参数配置 | `data` |
| `updateConfig` | PUT | `/system/config` | 修改参数配置 | `data` |
| `delConfig` | DELETE | `/system/config/{configId}` | 删除参数配置 | `configId` |
| `refreshCache` | DELETE | `/system/config/refreshCache` | 刷新参数缓存 | - |

### 4.8 通知公告

文件位置: `src/api/system/notice.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listNotice` | GET | `/system/notice/list` | 查询公告列表 | `query` |
| `getNotice` | GET | `/system/notice/{noticeId}` | 查询公告详细 | `noticeId` |
| `addNotice` | POST | `/system/notice` | 新增公告 | `data` |
| `updateNotice` | PUT | `/system/notice` | 修改公告 | `data` |
| `delNotice` | DELETE | `/system/notice/{noticeId}` | 删除公告 | `noticeId` |

---

## 5. 系统监控

### 5.1 在线用户

文件位置: `src/api/monitor/online.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `list` | GET | `/monitor/online/list` | 查询在线用户列表 | `query` |
| `forceLogout` | DELETE | `/monitor/online/{tokenId}` | 强退用户 | `tokenId` |

### 5.2 定时任务

文件位置: `src/api/monitor/job.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listJob` | GET | `/monitor/job/list` | 查询定时任务列表 | `query` |
| `getJob` | GET | `/monitor/job/{jobId}` | 查询定时任务详细 | `jobId` |
| `addJob` | POST | `/monitor/job` | 新增定时任务 | `data` |
| `updateJob` | PUT | `/monitor/job` | 修改定时任务 | `data` |
| `delJob` | DELETE | `/monitor/job/{jobId}` | 删除定时任务 | `jobId` |
| `changeJobStatus` | PUT | `/monitor/job/changeStatus` | 修改任务状态 | `jobId`, `status` |
| `runJob` | PUT | `/monitor/job/run` | 立即执行一次任务 | `jobId`, `jobGroup` |

### 5.3 调度日志

文件位置: `src/api/monitor/jobLog.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listJobLog` | GET | `/monitor/jobLog/list` | 查询调度日志列表 | `query` |
| `delJobLog` | DELETE | `/monitor/jobLog/{jobLogId}` | 删除调度日志 | `jobLogId` |
| `cleanJobLog` | DELETE | `/monitor/jobLog/clean` | 清空调度日志 | - |

### 5.4 登录日志

文件位置: `src/api/monitor/logininfor.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `list` | GET | `/monitor/logininfor/list` | 查询登录日志列表 | `query` |
| `delLogininfor` | DELETE | `/monitor/logininfor/{infoId}` | 删除登录日志 | `infoId` |
| `unlockLogininfor` | GET | `/monitor/logininfor/unlock/{userName}` | 解锁用户登录状态 | `userName` |
| `cleanLogininfor` | DELETE | `/monitor/logininfor/clean` | 清空登录日志 | - |

### 5.5 操作日志

文件位置: `src/api/monitor/operlog.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `list` | GET | `/monitor/operlog/list` | 查询操作日志列表 | `query` |
| `delOperlog` | DELETE | `/monitor/operlog/{operId}` | 删除操作日志 | `operId` |
| `cleanOperlog` | DELETE | `/monitor/operlog/clean` | 清空操作日志 | - |

### 5.6 缓存监控

文件位置: `src/api/monitor/cache.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `getCache` | GET | `/monitor/cache` | 查询缓存详细 | - |
| `listCacheName` | GET | `/monitor/cache/getNames` | 查询缓存名称列表 | - |
| `listCacheKey` | GET | `/monitor/cache/getKeys/{cacheName}` | 查询缓存键名列表 | `cacheName` |
| `getCacheValue` | GET | `/monitor/cache/getValue/{cacheName}/{cacheKey}` | 查询缓存内容 | `cacheName`, `cacheKey` |
| `clearCacheName` | DELETE | `/monitor/cache/clearCacheName/{cacheName}` | 清理指定名称缓存 | `cacheName` |
| `clearCacheKey` | DELETE | `/monitor/cache/clearCacheKey/{cacheKey}` | 清理指定键名缓存 | `cacheKey` |
| `clearCacheAll` | DELETE | `/monitor/cache/clearCacheAll` | 清理全部缓存 | - |

### 5.7 服务监控

文件位置: `src/api/monitor/server.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `getServer` | GET | `/monitor/server` | 获取服务信息 | - |

---

## 6. 系统工具

### 6.1 代码生成

文件位置: `src/api/tool/gen.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listTable` | GET | `/tool/gen/list` | 查询生成表数据 | `query` |
| `listDbTable` | GET | `/tool/gen/db/list` | 查询数据库列表 | `query` |
| `getGenTable` | GET | `/tool/gen/{tableId}` | 查询表详细信息 | `tableId` |
| `updateGenTable` | PUT | `/tool/gen` | 修改代码生成信息 | `data` |
| `importTable` | POST | `/tool/gen/importTable` | 导入表 | `data` |
| `createTable` | POST | `/tool/gen/createTable` | 创建表 | `data` |
| `previewTable` | GET | `/tool/gen/preview/{tableId}` | 预览生成代码 | `tableId` |
| `delTable` | DELETE | `/tool/gen/{tableId}` | 删除表数据 | `tableId` |
| `genCode` | GET | `/tool/gen/genCode/{tableName}` | 生成代码(自定义路径) | `tableName` |
| `synchDb` | GET | `/tool/gen/synchDb/{tableName}` | 同步数据库 | `tableName` |

---

## 7. 管理员管理

文件位置: `src/api/user/user.js`

| 函数名 | 方法 | 接口地址 | 描述 | 参数 |
|--------|------|----------|------|------|
| `listUser` | GET | `/user/user/list` | 查询管理员列表 | `query` |
| `getUser` | GET | `/user/user/{id}` | 查询管理员详细 | `id` |
| `addUser` | POST | `/user/user` | 新增管理员 | `data` |
| `updateUser` | PUT | `/user/user` | 修改管理员 | `data` |
| `delUser` | DELETE | `/user/user/{id}` | 删除管理员 | `id` |

---

## API 调用说明

所有 API 均通过 `@/utils/request` 封装的 axios 实例发起请求。

### 请求方式

- `GET`: 查询操作，参数通过 `params` 传递
- `POST`: 新增操作，参数通过 `data` 传递
- `PUT`: 修改操作，参数通过 `data` 传递
- `DELETE`: 删除操作，参数通过 URL 路径传递

### 使用示例

```javascript
import { listCoupons, addCoupons } from '@/api/coupons/coupons'

// 查询列表
listCoupons({ pageNum: 1, pageSize: 10 }).then(res => {
  console.log(res.data)
})

// 新增数据
addCoupons({ name: '优惠券名称', discount: 10 }).then(res => {
  console.log('新增成功')
})
```

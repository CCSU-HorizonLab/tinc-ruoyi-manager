-- ============================================
-- 修复三个业务菜单 404 问题 + 改为直接跳转（无下拉框）
-- 作者: shuguang-cmd
-- 日期: 2025-06-07
-- 数据库: kendeji (实际 menu_id: 2056, 2062, 2068)
-- ============================================

-- 步骤 1：先查看当前菜单状态（诊断用）
SELECT menu_id, menu_name, parent_id, menu_type, path, component, is_frame, visible
FROM sys_menu
WHERE menu_id IN (2056, 2062, 2068)
ORDER BY menu_id;

-- 步骤 2：查看子菜单（了解原有结构：按钮权限 + 页面）
SELECT menu_id, menu_name, parent_id, menu_type, path, component, perms
FROM sys_menu
WHERE parent_id IN (2056, 2062, 2068)
ORDER BY parent_id, menu_id;

-- 步骤 3：修复三个菜单 — 改为直接跳转（menu_type='C'），组件路径指向优化版 Vue 文件
-- 原问题：
--   1. menu_type='M'（目录）→ 侧边栏显示为下拉框
--   2. component 为空 → 无页面组件 → 点击404
--   3. is_frame=0（标记为外链）→ 路由格式异常

-- 修复 服务器管理 (menu_id=2056)
UPDATE sys_menu
SET
    menu_type = 'C',                          -- C=菜单（直接点击跳转），M=目录（下拉框）
    component = 'tinc/server/index',          -- 对应 src/views/tinc/server/index.vue
    is_frame = 1,                             -- 1=非外链
    visible = '0',                            -- 0=显示
    path = 'tinc-server',                     -- 路由地址 → 最终URL: /tinc-server
    icon = 'server'                           -- 侧边栏图标
WHERE menu_id = 2056;

-- 修复 内网管理 (menu_id=2062)
UPDATE sys_menu
SET
    menu_type = 'C',
    component = 'tinc/network/index',         -- 对应 src/views/tinc/network/index.vue
    is_frame = 1,
    visible = '0',
    path = 'tinc-network',                    -- 路由地址 → 最终URL: /tinc-network
    icon = 'network'
WHERE menu_id = 2062;

-- 修复 节点管理 (menu_id=2068)
UPDATE sys_menu
SET
    menu_type = 'C',
    component = 'tinc/node/index',            -- 对应 src/views/tinc/node/index.vue
    is_frame = 1,
    visible = '0',
    path = 'tinc-node',                       -- 路由地址 → 最终URL: /tinc-node
    icon = 'node'
WHERE menu_id = 2068;

-- 步骤 4：验证修复结果
SELECT menu_id, menu_name, parent_id, menu_type, path, component, is_frame, visible, icon
FROM sys_menu
WHERE menu_id IN (2056, 2062, 2068)
ORDER BY menu_id;

-- ============================================
-- 说明：原每个菜单下的子按钮（F类型：查询/新增/修改/删除/导出）保持不变，
-- 仍然用于权限控制（v-hasPermi），只是在侧边栏不再显示为下拉子项。
-- 原 "xxx页面" 子菜单（2074/2075/2076）已不需要，但保留也无影响。
-- ============================================

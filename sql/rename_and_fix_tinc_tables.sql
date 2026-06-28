-- ----------------------------------------------------
-- Tinc VPN 管理系统数据表重命名与结构修复脚本
-- 1. 将 "mange" 错拼修正为标准命名
-- 2. 修复各表字段错拼（如 start_Interat -> start_segment, start_post -> start_port 等）
-- 3. 在服务器表（tinc_server）及各表中补充 create_by 等审计字段，以适配若依 BaseEntity 基础类
-- ----------------------------------------------------

-- ====================================================
-- 1. 重命名数据表
-- ====================================================
RENAME TABLE `mange_server` TO `tinc_server`;
RENAME TABLE `tinc_network_mange` TO `tinc_network`;
RENAME TABLE `tinc_node_mange` TO `tinc_node`;


-- ====================================================
-- 2. 修复 tinc_server (原 mange_server) 字段与添加创建者
-- ====================================================
ALTER TABLE `tinc_server`
  -- 修复拼写错误
  CHANGE COLUMN `start_Interat` `start_segment` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '起始网段',
  CHANGE COLUMN `end_Interat` `end_segment` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '终止网段',
  CHANGE COLUMN `start_post` `start_port` int(11) NOT NULL DEFAULT 0 COMMENT '起始端口',
  CHANGE COLUMN `end_post` `end_port` int(11) NOT NULL DEFAULT 0 COMMENT '终止端口',
  CHANGE COLUMN `election` `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  -- 增加若依 BaseEntity 必须的审计字段 (表明是谁创建的服务器)
  ADD COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者' AFTER `status`,
  ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `create_by`,
  ADD COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者' AFTER `create_time`,
  ADD COLUMN `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `update_by`;


-- ====================================================
-- 3. 修复 tinc_network (原 tinc_network_mange) 字段
-- ====================================================
ALTER TABLE `tinc_network`
  -- 将 root_name (用户) 重命名为统一的 create_by (创建者)
  CHANGE COLUMN `root_name` `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '创建者',
  -- 将 explanation (备注) 重命名为若依标准字段 remark
  CHANGE COLUMN `explanation` `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  -- 补充更新者字段
  ADD COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者' AFTER `create_time`,
  ADD COLUMN `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `update_by`;


-- ====================================================
-- 4. 修复 tinc_node (原 tinc_node_mange) 字段
-- ====================================================
ALTER TABLE `tinc_node`
  -- 修复拼写/命名错误
  CHANGE COLUMN `use_name` `user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '所属用户',
  CHANGE COLUMN `table_ID` `device_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '设备ID',
  CHANGE COLUMN `explantion` `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  -- 增加审计字段
  ADD COLUMN `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者' AFTER `remark`,
  ADD COLUMN `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者' AFTER `create_time`,
  ADD COLUMN `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `update_by`;

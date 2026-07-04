-- ============================================================
-- 若依框架缺失表补建脚本
-- 数据库: kendeji
-- 解决: Table 'kendeji.sys_logininfor' doesn't exist
--       Table 'kendeji.sys_oper_log' doesn't exist
-- ============================================================

USE kendeji;

-- ----------------------------
-- 系统访问记录（登录日志）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_logininfor` (
  `info_id`        bigint(20)   NOT NULL AUTO_INCREMENT   COMMENT '访问ID',
  `user_name`      varchar(50)  DEFAULT ''                COMMENT '用户账号',
  `ipaddr`         varchar(128) DEFAULT ''                COMMENT '登录IP地址',
  `login_location` varchar(255) DEFAULT ''                COMMENT '登录地点',
  `browser`        varchar(50)  DEFAULT ''                COMMENT '浏览器类型',
  `os`             varchar(50)  DEFAULT ''                COMMENT '操作系统',
  `status`         char(1)      DEFAULT '0'               COMMENT '登录状态（0成功 1失败）',
  `msg`            varchar(255) DEFAULT ''                COMMENT '提示消息',
  `login_time`     datetime                               COMMENT '访问时间',
  PRIMARY KEY (`info_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='系统访问记录';

-- ----------------------------
-- 操作日志记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `oper_id`        bigint(20)    NOT NULL AUTO_INCREMENT  COMMENT '日志主键',
  `title`          varchar(50)   DEFAULT ''               COMMENT '模块标题',
  `business_type`  int(2)        DEFAULT 0                COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method`         varchar(200)  DEFAULT ''               COMMENT '方法名称',
  `request_method` varchar(10)   DEFAULT ''               COMMENT '请求方式',
  `operator_type`  int(1)        DEFAULT 0                COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name`      varchar(50)   DEFAULT ''               COMMENT '操作人员',
  `dept_name`      varchar(50)   DEFAULT ''               COMMENT '部门名称',
  `oper_url`       varchar(255)  DEFAULT ''               COMMENT '请求URL',
  `oper_ip`        varchar(128)  DEFAULT ''               COMMENT '主机地址',
  `oper_location`  varchar(255)  DEFAULT ''               COMMENT '操作地点',
  `oper_param`     varchar(2000) DEFAULT ''               COMMENT '请求参数',
  `json_result`    varchar(2000) DEFAULT ''               COMMENT '返回参数',
  `status`         int(1)        DEFAULT 0                COMMENT '操作状态（0正常 1异常）',
  `error_msg`      varchar(2000) DEFAULT ''               COMMENT '错误消息',
  `oper_time`      datetime                               COMMENT '操作时间',
  `cost_time`      bigint(20)    DEFAULT 0                COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt`  (`business_type`),
  KEY `idx_sys_oper_log_s`   (`status`),
  KEY `idx_sys_oper_log_ot`  (`oper_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='操作日志记录';

SELECT 'sys_logininfor 和 sys_oper_log 建表完成' AS result;

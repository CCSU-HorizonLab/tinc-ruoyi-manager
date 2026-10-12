-- 管理后台资源授权。先在备份恢复的测试库执行并验证，生产执行需单独批准。
-- 不根据创建者或名字回填授权，不修改已有 Tinc 业务表或运行时配置。
CREATE TABLE IF NOT EXISTS tinc_resource_grant (
  user_id BIGINT NOT NULL COMMENT '被授权后台账号 ID',
  resource_type VARCHAR(16) NOT NULL COMMENT 'SERVER 或 NETWORK',
  resource_id BIGINT NOT NULL COMMENT '稳定资源 ID',
  granted_by BIGINT NOT NULL COMMENT '执行分配的超级管理员 ID',
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, resource_type, resource_id),
  KEY idx_tinc_grant_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 普通账号默认空授权；通过系统用户页面的“网络资源授权”逐个明确分配。
-- 授权查询始终 JOIN 现存资源，不根据名称回退。删除后残留授权不具有访问效力。
-- 不要手工复用已删除资源的主键 ID；历史授权可在用户授权页面清空。

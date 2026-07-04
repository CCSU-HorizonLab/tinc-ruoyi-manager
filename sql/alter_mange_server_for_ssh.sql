-- ============================================================
-- Tinc 产品化改造：为 mange_server 表增加 SSH 远程管理字段
--
-- 背景：管理后台和 Tinc VPN 网关不在同一台机器上时，
--       需要通过 SSH 将配置文件推送到远程网关。
--       本脚本在 mange_server 表中增加 SSH 连接所需的字段。
--
-- 注意：
--   1. 如果某字段留空，则取 application.yml 中的全局默认值
--   2. 私钥认证优先于密码认证
--   3. 执行前请备份数据库！
-- ============================================================

ALTER TABLE mange_server
    ADD COLUMN ssh_port     INT          DEFAULT 22    COMMENT 'SSH 端口',
    ADD COLUMN ssh_user     VARCHAR(64)  DEFAULT 'root' COMMENT 'SSH 登录用户名',
    ADD COLUMN ssh_key_path VARCHAR(255)               COMMENT 'SSH 私钥路径（优先于密码认证）',
    ADD COLUMN ssh_password VARCHAR(128)               COMMENT 'SSH 密码（未配置私钥时使用）';

-- 验证字段是否添加成功
DESC mange_server;

-- TincLink 第一阶段：Server -> Network -> Node 稳定 ID 迁移
-- 适用：MySQL 5.7 / InnoDB。先在备份恢复出的测试库验证；禁止直接在生产执行。
-- 本脚本不删除旧 name 字段，不处理 /etc/tinc，也不自动清理异常业务数据。

-- STEP 0：执行 ALTER 前先人工检查映射是否唯一。
SELECT server_name, COUNT(*) AS duplicate_count
FROM tinc_server
GROUP BY server_name
HAVING COUNT(*) > 1;

SELECT network_name, COUNT(*) AS duplicate_count
FROM tinc_network
GROUP BY network_name
HAVING COUNT(*) > 1;

SELECT n.id, n.network_name
FROM tinc_network n
LEFT JOIN tinc_server s ON s.server_name = n.server_name
GROUP BY n.id, n.network_name
HAVING COUNT(s.Id) <> 1;

SELECT d.id, d.node_name, d.network_name
FROM tinc_node d
LEFT JOIN tinc_network n ON n.network_name = d.network_name
GROUP BY d.id, d.node_name, d.network_name
HAVING COUNT(n.id) <> 1;

-- STEP 1：先增加 nullable 稳定关系字段。
ALTER TABLE tinc_network ADD COLUMN server_id BIGINT NULL COMMENT '所属接入服务器ID' AFTER id;
ALTER TABLE tinc_node ADD COLUMN network_id BIGINT NULL COMMENT '所属网络ID' AFTER id;

-- STEP 2：再次在数据库内强制验证一对一；存在歧义或孤儿记录时 SIGNAL 并停止回填。
DELIMITER $$
DROP PROCEDURE IF EXISTS backfill_tinc_stable_ids$$
CREATE PROCEDURE backfill_tinc_stable_ids()
BEGIN
    IF EXISTS (
        SELECT 1 FROM tinc_server GROUP BY server_name HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '历史数据存在歧义：server_name 对应多个 Server';
    END IF;

    IF EXISTS (
        SELECT 1 FROM tinc_network GROUP BY network_name HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '历史数据存在歧义：network_name 对应多个 Network';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tinc_network n
        LEFT JOIN tinc_server s ON s.server_name = n.server_name
        WHERE s.Id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '历史数据存在孤儿 Network：无法精确映射 Server';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tinc_node d
        LEFT JOIN tinc_network n ON n.network_name = d.network_name
        WHERE n.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '历史数据存在孤儿 Node：无法精确映射 Network';
    END IF;

    UPDATE tinc_network n
    INNER JOIN tinc_server s ON s.server_name = n.server_name
    SET n.server_id = s.Id
    WHERE n.server_id IS NULL;

    UPDATE tinc_node d
    INNER JOIN tinc_network n ON n.network_name = d.network_name
    SET d.network_id = n.id
    WHERE d.network_id IS NULL;
END$$
DELIMITER ;

CALL backfill_tinc_stable_ids();
DROP PROCEDURE backfill_tinc_stable_ids;

-- STEP 3：回填结果与业务冲突检查。任何结果非空都应停止最终约束步骤。
SELECT * FROM tinc_network WHERE server_id IS NULL;
SELECT * FROM tinc_node WHERE network_id IS NULL;

SELECT network_name, COUNT(*) AS duplicate_count
FROM tinc_network GROUP BY network_name HAVING COUNT(*) > 1;

SELECT port, COUNT(*) AS duplicate_count
FROM tinc_network GROUP BY port HAVING COUNT(*) > 1;

SELECT segment, COUNT(*) AS duplicate_count
FROM tinc_network GROUP BY segment HAVING COUNT(*) > 1;

SELECT network_id, node_name, COUNT(*) AS duplicate_count
FROM tinc_node GROUP BY network_id, node_name HAVING COUNT(*) > 1;

SELECT network_id, network_ip, COUNT(*) AS duplicate_count
FROM tinc_node GROUP BY network_id, network_ip HAVING COUNT(*) > 1;

-- STEP 4A：第一阶段可先建立普通查询索引；生产执行仍需走备份、维护窗口和回滚审批。
ALTER TABLE tinc_network ADD INDEX idx_network_server_id (server_id);
ALTER TABLE tinc_node ADD INDEX idx_node_network_id (network_id);

-- STEP 4B：仅在 STEP 3 全部为空、表引擎均为 InnoDB 且人工确认后另行执行。
-- 生产现有三个主键是 INT(11)，而本阶段关系字段按目标模型增加为 BIGINT。
-- 加外键前必须在独立维护窗口先评估并统一父子列类型；不能直接执行下面的 FK。
-- ALTER TABLE tinc_server MODIFY Id BIGINT NOT NULL AUTO_INCREMENT;
-- ALTER TABLE tinc_network MODIFY id BIGINT NOT NULL AUTO_INCREMENT,
--     MODIFY server_id BIGINT NOT NULL;
-- ALTER TABLE tinc_node MODIFY id BIGINT NOT NULL AUTO_INCREMENT,
--     MODIFY network_id BIGINT NOT NULL;
-- ALTER TABLE tinc_network ADD CONSTRAINT uk_tinc_network_name UNIQUE (network_name);
-- ALTER TABLE tinc_node ADD CONSTRAINT uk_tinc_node_network_name UNIQUE (network_id, node_name);
-- ALTER TABLE tinc_node ADD CONSTRAINT uk_tinc_node_network_ip UNIQUE (network_id, network_ip);
-- ALTER TABLE tinc_network ADD CONSTRAINT fk_tinc_network_server
--     FOREIGN KEY (server_id) REFERENCES tinc_server (Id);
-- ALTER TABLE tinc_node ADD CONSTRAINT fk_tinc_node_network
--     FOREIGN KEY (network_id) REFERENCES tinc_network (id);

-- 当前单 Access Runtime 还要求 port 全局唯一、/24 segment 不重复或重叠。
-- 若 STEP 3 报告历史端口/网段冲突，应先制定人工迁移方案，不能自动改端口或运行网络。

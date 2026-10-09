-- ==============================================================================
-- TincLink Phase 3: 节点全局唯一接入码（access_code）数据库迁移与历史数据回填脚本
-- 适用：MySQL 5.7+ / MariaDB / InnoDB
-- 本脚本绝不删除现有 Node，不修改 nodeId、networkId、nodeName、password、Key Pair、Token 或 Runtime。
-- ==============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- STEP 0: 前置检查（检查当前 tinc_node 状态）
SELECT id, node_name, network_name, network_ip
FROM tinc_node
ORDER BY id ASC;

-- STEP 1: 先增加 nullable 的 access_code 字段（32 字符，存放人类可读格式 TL-XXXX-XXXX）
-- 检查字段是否已存在，若不存在则添加
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tinc_node'
  AND COLUMN_NAME = 'access_code';

SET @stmt = IF(@col_exists = 0,
    'ALTER TABLE tinc_node ADD COLUMN access_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT \'节点全局唯一接入码\' AFTER network_id',
    'ALTER TABLE tinc_node MODIFY COLUMN access_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT \'节点全局唯一接入码\'');
PREPARE add_col_stmt FROM @stmt;
EXECUTE add_col_stmt;
DEALLOCATE PREPARE add_col_stmt;

-- STEP 2: 创建并执行安全回填存储过程
-- 生成格式：TL-XXXX-XXXX（前缀 TL-，两组各4字符，字符集排除 0/O/1/I/L 等易混淆字符）
-- 字符集（31字符）：23456789ABCDEFGHJKMNPQRSTUVWXYZ
DELIMITER $$
DROP PROCEDURE IF EXISTS backfill_tinc_node_access_code$$
CREATE PROCEDURE backfill_tinc_node_access_code()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_node_id BIGINT;
    DECLARE v_new_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_code_exists INT;
    DECLARE v_chars VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    DECLARE v_idx INT;
    DECLARE v_retry INT;

    -- 遍历所有尚未分配 access_code 的节点
    DECLARE node_cursor CURSOR FOR
        SELECT id FROM tinc_node WHERE access_code IS NULL OR access_code = '';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN node_cursor;

    read_loop: LOOP
        FETCH node_cursor INTO v_node_id;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET v_retry = 0;
        retry_loop: LOOP
            SET v_retry = v_retry + 1;
            IF v_retry > 100 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = '回填 access_code 冲突重试次数超限，请检查随机字符集';
            END IF;

            -- 拼接 TL-XXXX-XXXX
            SET v_new_code = 'TL-';
            -- 前4位随机字符
            SET v_idx = 0;
            WHILE v_idx < 4 DO
                SET v_new_code = CONCAT(v_new_code, SUBSTRING(v_chars, FLOOR(1 + RAND() * CHAR_LENGTH(v_chars)), 1));
                SET v_idx = v_idx + 1;
            END WHILE;

            SET v_new_code = CONCAT(v_new_code, '-');

            -- 后4位随机字符
            SET v_idx = 0;
            WHILE v_idx < 4 DO
                SET v_new_code = CONCAT(v_new_code, SUBSTRING(v_chars, FLOOR(1 + RAND() * CHAR_LENGTH(v_chars)), 1));
                SET v_idx = v_idx + 1;
            END WHILE;

            -- 检查全局是否已有该 access_code
            SELECT COUNT(*) INTO v_code_exists
            FROM tinc_node
            WHERE access_code = v_new_code;

            IF v_code_exists = 0 THEN
                -- 更新当前节点
                UPDATE tinc_node
                SET access_code = v_new_code
                WHERE id = v_node_id;
                LEAVE retry_loop;
            END IF;
        END LOOP retry_loop;
    END LOOP read_loop;

    CLOSE node_cursor;
END$$
DELIMITER ;

CALL backfill_tinc_node_access_code();
DROP PROCEDURE IF EXISTS backfill_tinc_node_access_code;

-- STEP 3: 验证回填结果（必须 NULL = 0 且重复值 = 0）
SELECT COUNT(*) AS null_count
FROM tinc_node
WHERE access_code IS NULL OR access_code = '';

SELECT access_code, COUNT(*) AS duplicate_count
FROM tinc_node
GROUP BY access_code
HAVING COUNT(*) > 1;

-- STEP 4: 建立 UNIQUE 唯一索引保证数据库级防冲突兜底
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tinc_node'
  AND INDEX_NAME = 'uk_tinc_node_access_code';

SET @stmt = IF(@idx_exists = 0,
    'ALTER TABLE tinc_node ADD CONSTRAINT uk_tinc_node_access_code UNIQUE (access_code)',
    'SELECT "Index uk_tinc_node_access_code already exists" AS notice');
PREPARE add_idx_stmt FROM @stmt;
EXECUTE add_idx_stmt;
DEALLOCATE PREPARE add_idx_stmt;

-- STEP 5: 将 access_code 列设置为 NOT NULL
ALTER TABLE tinc_node MODIFY COLUMN access_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点全局唯一接入码';

-- STEP 6: 输出最终回填验证结果
SELECT id, network_id, node_name, access_code, network_name, network_ip
FROM tinc_node
ORDER BY id ASC;

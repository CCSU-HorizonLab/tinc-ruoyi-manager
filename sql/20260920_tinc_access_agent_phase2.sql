-- TincLink Phase 2: Access Agent fields and per-Access-Server resource scope.
-- MySQL 5.7 / InnoDB. This migration depends on 20260919_tinc_stable_ids_phase1.sql.
-- DO NOT run directly on production. It does not touch /etc/tinc or any running service.

-- Preflight: record historical conflicts before adding the composite lookup indexes.
-- Existing production data may contain same-server segment/port conflicts. Phase 2 preserves
-- those rows and relies on Service validation to prevent new conflicts; do not delete or rename
-- a running network merely to make a UNIQUE constraint succeed.
SELECT server_id, network_name, COUNT(*) AS duplicate_count
FROM tinc_network
GROUP BY server_id, network_name
HAVING COUNT(*) > 1;

SELECT server_id, port, COUNT(*) AS duplicate_count
FROM tinc_network
GROUP BY server_id, port
HAVING COUNT(*) > 1;

SELECT server_id, segment, COUNT(*) AS duplicate_count
FROM tinc_network
GROUP BY server_id, segment
HAVING COUNT(*) > 1;

ALTER TABLE tinc_server
    ADD COLUMN runtime_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL or AGENT' AFTER ssh_password,
    ADD COLUMN agent_port INT NULL COMMENT 'Access Agent HTTP port' AFTER runtime_type,
    ADD COLUMN agent_status VARCHAR(16) NOT NULL DEFAULT 'UNREACHABLE' COMMENT 'LOCAL/ONLINE/DEGRADED/UNREACHABLE' AFTER agent_port,
    ADD COLUMN agent_version VARCHAR(64) NULL AFTER agent_status,
    ADD COLUMN agent_last_seen DATETIME NULL AFTER agent_version,
    ADD COLUMN agent_secret VARCHAR(512) NULL COMMENT 'Never returned by list/detail APIs' AFTER agent_last_seen,
    ADD COLUMN agent_id VARCHAR(64) NULL AFTER agent_secret,
    ADD COLUMN agent_cpu_usage DECIMAL(6,2) NULL AFTER agent_id,
    ADD COLUMN agent_memory_usage DECIMAL(6,2) NULL AFTER agent_cpu_usage,
    ADD COLUMN agent_network_count INT NULL AFTER agent_memory_usage;

-- Existing rows remain LOCAL so current co-located production behavior is preserved after a future approved deployment.
UPDATE tinc_server
SET runtime_type = 'LOCAL', agent_status = 'LOCAL'
WHERE runtime_type IS NULL OR runtime_type = 'LOCAL';

ALTER TABLE tinc_network
    ADD INDEX idx_tinc_network_server_name (server_id, network_name),
    ADD INDEX idx_tinc_network_server_port (server_id, port),
    ADD INDEX idx_tinc_network_server_segment (server_id, segment);

CREATE INDEX idx_tinc_server_runtime_status ON tinc_server (runtime_type, agent_status);

-- Optional hard constraints after a separately approved production-data cleanup. Keep these
-- disabled while historical duplicates exist; the current Service layer enforces the same rules
-- for all new and modified records.
-- ALTER TABLE tinc_network
--     ADD CONSTRAINT uk_tinc_network_server_name UNIQUE (server_id, network_name),
--     ADD CONSTRAINT uk_tinc_network_server_port UNIQUE (server_id, port),
--     ADD CONSTRAINT uk_tinc_network_server_segment UNIQUE (server_id, segment);

-- server_ip is intentionally reused in Phase 2 as both Management->Agent control address
-- and Client->Tinc public address. Split management_address/public_address in Phase 3 if NAT requires it.

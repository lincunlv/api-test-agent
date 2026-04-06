CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    skill_code VARCHAR(16) NOT NULL,
    skill_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt TEXT NOT NULL,
    request_json JSON NULL,
    workspace_path VARCHAR(512) NOT NULL,
    message VARCHAR(1024) NULL,
    last_error TEXT NULL,
    source_lookup_applied TINYINT(1) NOT NULL DEFAULT 0,
    execution_event_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_task_id (task_id),
    KEY idx_agent_task_status_created_at (status, created_at),
    KEY idx_agent_task_skill_code_created_at (skill_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_task_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1024) NULL,
    details_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    seq_no INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_event_task_seq (task_id, seq_no),
    KEY idx_agent_task_event_task_created_at (task_id, created_at),
    CONSTRAINT fk_agent_task_event_task_id FOREIGN KEY (task_id)
        REFERENCES agent_task (task_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_task_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    artifact_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    storage_type VARCHAR(32) NOT NULL DEFAULT 'FILE',
    storage_path VARCHAR(1024) NOT NULL,
    file_size BIGINT NULL,
    content_text LONGTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_artifact_name (task_id, artifact_name),
    KEY idx_agent_task_artifact_task_created_at (task_id, created_at),
    CONSTRAINT fk_agent_task_artifact_task_id FOREIGN KEY (task_id)
        REFERENCES agent_task (task_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
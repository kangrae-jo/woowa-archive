CREATE TABLE IF NOT EXISTS queue_monster (
    monster_id BIGINT NOT NULL AUTO_INCREMENT,
    prompt VARCHAR(1000) NOT NULL,
    image TEXT NULL,
    PRIMARY KEY (monster_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS image_generation_job (
    job_id BIGINT NOT NULL AUTO_INCREMENT,
    monster_id BIGINT NOT NULL,
    prompt VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NULL,
    claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    last_error VARCHAR(2000),
    PRIMARY KEY (job_id),
    CONSTRAINT fk_job_monster FOREIGN KEY (monster_id) REFERENCES queue_monster (monster_id),
    CONSTRAINT chk_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_job_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_job_claim CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND deadline_at IS NOT NULL AND started_at IS NOT NULL)
        OR
        (status <> 'RUNNING' AND claim_token IS NULL AND deadline_at IS NULL)
    ),
    CONSTRAINT chk_job_finished CHECK (
        (status IN ('SUCCEEDED', 'FAILED') AND finished_at IS NOT NULL)
        OR
        (status IN ('PENDING', 'RUNNING') AND finished_at IS NULL)
    ),
    INDEX job_ready_idx (status, next_attempt_at, job_id),
    INDEX job_deadline_idx (status, deadline_at, job_id)
) ENGINE = InnoDB;

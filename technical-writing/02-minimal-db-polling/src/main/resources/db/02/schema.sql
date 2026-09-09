CREATE TABLE IF NOT EXISTS monster (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt VARCHAR(1000) NOT NULL,
    image TEXT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS image_generation_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prompt VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX request_created_idx (created_at, id)
) ENGINE = InnoDB;

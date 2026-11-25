-- 为同部门文件去重增加数据库级约束。
-- 部门隔离要求相同 SHA-256 可以存在于不同部门。

ALTER TABLE document_version
    ADD COLUMN dept_id BIGINT NOT NULL;

ALTER TABLE document_version
    ADD CONSTRAINT fk_document_version_dept
        FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uk_document_version_dept_sha256_current
    ON document_version (dept_id, sha256)
    WHERE sha256 IS NOT NULL
      AND is_current = 1;

CREATE INDEX idx_document_version_dept_sha256
    ON document_version (dept_id, sha256);

-- 邮件元数据与附件选择性导入。last_uid 表示元数据已成功发现的最高 UID。
ALTER TABLE dept_email_config
    ADD COLUMN uid_validity BIGINT;

-- 旧游标来自自动下载流程，新邮件元数据表为空，因此从当前 INBOX 重新发现。
UPDATE dept_email_config
SET last_uid = 0,
    uid_validity = NULL;

CREATE TABLE email_message (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    email_config_id BIGINT NOT NULL,
    uid_validity BIGINT NOT NULL,
    email_uid BIGINT NOT NULL,
    sender VARCHAR(128) NOT NULL,
    subject VARCHAR(255),
    received_at TIMESTAMP,
    body_text TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'UNPROCESSED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_email_message_uid UNIQUE (email_config_id, uid_validity, email_uid),
    CONSTRAINT chk_email_message_status CHECK (status IN ('UNPROCESSED', 'PROCESSED', 'IGNORED')),
    CONSTRAINT fk_email_message_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE CASCADE,
    CONSTRAINT fk_email_message_config FOREIGN KEY (email_config_id)
        REFERENCES dept_email_config(id) ON DELETE CASCADE
);
CREATE INDEX idx_email_message_dept_status ON email_message(dept_id, status, received_at DESC);

CREATE TABLE email_attachment (
    id BIGSERIAL PRIMARY KEY,
    email_id BIGINT NOT NULL,
    part_id VARCHAR(128) NOT NULL,
    attachment_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    attachment_size BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'UNPROCESSED',
    document_version_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_email_attachment_part UNIQUE (email_id, part_id),
    CONSTRAINT chk_email_attachment_size CHECK (attachment_size IS NULL OR attachment_size >= 0),
    CONSTRAINT chk_email_attachment_status
        CHECK (status IN ('UNPROCESSED', 'IMPORTING', 'IMPORTED', 'IGNORED', 'FAILED')),
    CONSTRAINT fk_email_attachment_email FOREIGN KEY (email_id)
        REFERENCES email_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_email_attachment_version FOREIGN KEY (document_version_id)
        REFERENCES document_version(id) ON DELETE SET NULL
);
CREATE INDEX idx_email_attachment_email_status ON email_attachment(email_id, status);
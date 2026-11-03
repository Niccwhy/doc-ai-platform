-- =============================================================
-- doc-ai-platform 数据库基线 v2（多租户 + 任务派发 + RAG 抽取）
--
-- 设计基准：partner 提供的《数据库表.md》，在此基础上补充了以下字段，
-- 每处均以「补充：」注释说明原因，未改动其原有命名与结构。
--
-- 注意：本脚本用于「清库重建」。已有数据卷需先删除 data/postgres 再启动，
-- 或手动执行本脚本（表已存在时会报错，属预期）。
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================
-- 1. 组织架构与权限
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    parent_id   BIGINT DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 统一用带时区类型
    deleted_at  TIMESTAMPTZ
);
COMMENT ON TABLE sys_dept IS '部门（租户），数据隔离边界';

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGSERIAL PRIMARY KEY,
    dept_id       BIGINT NOT NULL REFERENCES sys_dept(id),
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name     VARCHAR(64),
    role          VARCHAR(32) NOT NULL,                        -- ADMIN / DISPATCHER / PROCESSOR
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',       -- 补充：改用可读状态，与 task.status 风格一致
    email         VARCHAR(128),                                -- 补充：用于任务通知与账号联系
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_user_dept ON sys_user(dept_id);
COMMENT ON COLUMN sys_user.role IS 'ADMIN-系统管理员 DISPATCHER-分配员 PROCESSOR-处理员';

CREATE TABLE IF NOT EXISTS dept_email_config (
    id            BIGSERIAL PRIMARY KEY,
    dept_id       BIGINT NOT NULL REFERENCES sys_dept(id),
    email_address VARCHAR(128) NOT NULL,
    imap_host     VARCHAR(128) NOT NULL,
    imap_port     INT NOT NULL DEFAULT 993,
    use_ssl       SMALLINT NOT NULL DEFAULT 1,                 -- 补充：993 需 SSL，465/143 可能不需要
    auth_code     VARCHAR(255) NOT NULL,
    folder        VARCHAR(64) NOT NULL DEFAULT 'INBOX',        -- 补充：可指定收件文件夹
    last_uid      BIGINT NOT NULL DEFAULT 0,                   -- 补充：增量拉取位点，避免重复入库
    last_check_at TIMESTAMPTZ,                                 -- 补充：上次拉取时间，便于观测
    enabled       SMALLINT NOT NULL DEFAULT 1,                 -- 补充：可临时停用某邮箱
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dept_id, email_address)
);

-- =============================================================
-- 2. 资产库与标签
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_tag (
    id         BIGSERIAL PRIMARY KEY,
    dept_id    BIGINT NOT NULL REFERENCES sys_dept(id),
    tag_name   VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dept_id, tag_name)
);

CREATE TABLE IF NOT EXISTS document (
    id            BIGSERIAL PRIMARY KEY,
    dept_id       BIGINT NOT NULL REFERENCES sys_dept(id),
    uploader_id   BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_type     VARCHAR(32) NOT NULL,                         -- docx / xlsx / md / txt / pdf
    file_category VARCHAR(32) NOT NULL,                         -- SOURCE / TEMPLATE / OUTPUT
    file_size     BIGINT NOT NULL,
    sha256        CHAR(64) NOT NULL,
    storage_key   VARCHAR(512) NOT NULL,
    source_type   VARCHAR(32) NOT NULL DEFAULT 'WEB',           -- WEB / EMAIL
    status        VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',      -- UPLOADED / PARSING / PARSED / FAILED
    error_msg     TEXT,                                          -- 补充：记录解析失败原因，便于排查
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    UNIQUE (dept_id, sha256)
);
CREATE INDEX IF NOT EXISTS idx_doc_dept_cat ON document(dept_id, file_category);
CREATE INDEX IF NOT EXISTS idx_doc_status ON document(status);

CREATE TABLE IF NOT EXISTS document_tag_rel (
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    tag_id      BIGINT NOT NULL REFERENCES sys_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

-- =============================================================
-- 3. 文档解析与向量（RAG 基础）
-- =============================================================

CREATE TABLE IF NOT EXISTS document_block (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    block_index  INT NOT NULL,
    block_type   VARCHAR(32) NOT NULL,                          -- TITLE / PARAGRAPH / TABLE
    content      TEXT NOT NULL,
    table_json   JSONB,
    heading_path VARCHAR(512),                                  -- 补充：标题层级，填表与溯源需要
    cell_ref     VARCHAR(128),                                  -- 补充：表格坐标（如 Sheet1!A1:C5）
    embedding    vector(1536),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_block_doc ON document_block(document_id);
-- HNSW 余弦距离索引：万级向量下检索为毫秒级
CREATE INDEX IF NOT EXISTS idx_block_embedding
    ON document_block USING hnsw (embedding vector_cosine_ops);

-- =============================================================
-- 4. 抽取 Schema
-- =============================================================

CREATE TABLE IF NOT EXISTS extract_schema (
    id          BIGSERIAL PRIMARY KEY,
    dept_id     BIGINT NOT NULL REFERENCES sys_dept(id),
    creator_id  BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    name        VARCHAR(128) NOT NULL,
    prompt_text TEXT,
    schema_json JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,                                    -- 补充：人工微调后需记录修改时间
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_schema_dept ON extract_schema(dept_id);
COMMENT ON COLUMN extract_schema.schema_json IS
    '结构：{"fields":[{"key":"amount","label":"金额","type":"number","unit":"万元","required":true,"description":"合同总金额"}]}';

-- =============================================================
-- 5. 任务流转
-- =============================================================

CREATE TABLE IF NOT EXISTS task (
    id              BIGSERIAL PRIMARY KEY,
    dept_id         BIGINT NOT NULL REFERENCES sys_dept(id),
    creator_id      BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    assignee_id     BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    task_type       VARCHAR(32) NOT NULL,                       -- EXTRACT（仅抽取） / FILL（填表）
    schema_id       BIGINT REFERENCES extract_schema(id) ON DELETE SET NULL,
    template_doc_id BIGINT REFERENCES document(id) ON DELETE SET NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',     -- PENDING / ASSIGNED / PROCESSING / COMPLETED / FAILED
    progress        INT NOT NULL DEFAULT 0,                     -- 补充：SSE 进度推送必需
    payload         TEXT,                                       -- 补充：任务入参快照（字段范围等）
    result_json     TEXT,                                       -- 补充：执行结果摘要
    result_doc_id   BIGINT REFERENCES document(id) ON DELETE SET NULL,
    error_msg       TEXT,                                       -- 补充：失败原因
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,                                -- 补充：耗时统计
    completed_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_task_dept_status ON task(dept_id, status);
CREATE INDEX IF NOT EXISTS idx_task_assignee ON task(assignee_id);
CREATE INDEX IF NOT EXISTS idx_task_creator ON task(creator_id);

CREATE TABLE IF NOT EXISTS task_document_rel (
    task_id     BIGINT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, document_id)
);

-- =============================================================
-- 6. 抽取结果与审计
-- =============================================================

CREATE TABLE IF NOT EXISTS extract_result (
    id                BIGSERIAL PRIMARY KEY,
    task_id           BIGINT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    document_id       BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    field_key         VARCHAR(64) NOT NULL,
    field_value       TEXT,
    raw_value         TEXT,                                     -- 补充：模型原始输出，便于对比归一化前后
    confidence        NUMERIC(5,4),
    evidence_block_id BIGINT REFERENCES document_block(id) ON DELETE SET NULL,
    evidence_text     TEXT,                                     -- 补充：证据原文快照，块被清理后仍可追溯
    is_modified       SMALLINT NOT NULL DEFAULT 0,              -- 处理员是否人工修正
    verified          SMALLINT NOT NULL DEFAULT 0,              -- 补充：独立标记人工确认，与"是否改过"区分
    modified_by       BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,   -- 补充：修正人，用于追责
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ                               -- 补充：修正时间
);
CREATE INDEX IF NOT EXISTS idx_result_task ON extract_result(task_id);
CREATE INDEX IF NOT EXISTS idx_result_field ON extract_result(field_key);
CREATE INDEX IF NOT EXISTS idx_result_doc ON extract_result(document_id);

CREATE TABLE IF NOT EXISTS task_log (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    operator_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    action      VARCHAR(64) NOT NULL,                           -- CREATE / ASSIGN / START / EXTRACT / COMPLETE / FAIL
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_task_log_task ON task_log(task_id);

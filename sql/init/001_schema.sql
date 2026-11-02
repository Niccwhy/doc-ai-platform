-- =============================================================
-- doc-ai-platform 数据库初始化脚本
-- 注意：本目录挂载到 /docker-entrypoint-initdb.d，仅首次初始化时执行。
-- 若 data/postgres 卷已存在，需手动 docker exec 执行本脚本。
-- =============================================================

-- 扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================
-- 1. 文档表：记录上传的原始文档（docx/xlsx/md/txt）
-- =============================================================
CREATE TABLE IF NOT EXISTS document (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,          -- 原始文件名
    file_type     VARCHAR(16)  NOT NULL,          -- docx / xlsx / md / txt
    object_key    VARCHAR(512),                   -- MinIO 对象键
    size_bytes    BIGINT,
    status        VARCHAR(16) NOT NULL DEFAULT 'UPLOADED', -- UPLOADED/PARSING/PARSED/FAILED
    error_msg     TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_document_type ON document(file_type);

-- =============================================================
-- 2. 文档片段表：解析后的文本/表格切片，含向量字段（检索用）
-- =============================================================
CREATE TABLE IF NOT EXISTS doc_chunk (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    seq           INT NOT NULL,                   -- 文档内序号
    chunk_type    VARCHAR(16) NOT NULL,           -- text / table
    heading_path  VARCHAR(512),                   -- 标题层级路径（如 1.1.2）
    content       TEXT NOT NULL,                  -- 原文内容
    embedding     vector(1024),                   -- 文本向量（维度取决于 embedding 模型，可调）
    cell_ref      VARCHAR(64),                    -- 表格片段：sheet 名 + 单元格坐标（如 Sheet1!A1:D10）
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_doc_chunk_doc ON doc_chunk(document_id);
-- 向量索引（数据量小可先不建，量大后启用 HNSW）
-- CREATE INDEX IF NOT EXISTS idx_doc_chunk_embedding ON doc_chunk USING hnsw (embedding vector_cosine_ops);

-- =============================================================
-- 3. 抽取字段定义表：用户定义的待抽取字段
-- =============================================================
CREATE TABLE IF NOT EXISTS extract_field (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,          -- 字段名（如 甲方）
    alias         VARCHAR(512),                   -- 同义别名，逗号分隔
    data_type     VARCHAR(32) NOT NULL DEFAULT 'string', -- string/number/date/boolean
    unit          VARCHAR(32),                    -- 单位（如 万元、%）
    description   TEXT,                           -- 字段说明，供 LLM 理解
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================
-- 4. 抽取结果表：字段值 + 原文证据链（可溯源）
-- =============================================================
CREATE TABLE IF NOT EXISTS extract_result (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    field_id      BIGINT REFERENCES extract_field(id) ON DELETE SET NULL,
    field_name    VARCHAR(128) NOT NULL,          -- 冗余字段名，便于查询
    value         TEXT,                           -- 归一化后的字段值
    raw_value     TEXT,                           -- 原文取值
    confidence    NUMERIC(5,4),                   -- 置信度 0~1
    chunk_id      BIGINT REFERENCES doc_chunk(id) ON DELETE SET NULL, -- 证据来源片段
    evidence      TEXT,                           -- 原文证据（连续子串）
    verified      BOOLEAN NOT NULL DEFAULT false, -- 是否人工审核通过
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_extract_result_doc ON extract_result(document_id);
CREATE INDEX IF NOT EXISTS idx_extract_result_field ON extract_result(field_name);

-- =============================================================
-- 5. 异步任务表：抽取/填表任务状态跟踪
-- =============================================================
CREATE TABLE IF NOT EXISTS task (
    id            BIGSERIAL PRIMARY KEY,
    task_id       VARCHAR(64) NOT NULL UNIQUE,    -- 对外暴露的 UUID
    task_type     VARCHAR(32) NOT NULL,           -- EXTRACT / FILL_TABLE / DOC_OP
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/SUCCESS/FAILED
    progress      INT NOT NULL DEFAULT 0,         -- 0~100
    payload       TEXT,                           -- 任务入参（JSON 字符串）
    result        TEXT,                           -- 任务结果（JSON 字符串）
    error_msg     TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_task_status ON task(status);

-- ==========================================
-- 0. 扩展安装
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- 用于生成 UUID
CREATE EXTENSION IF NOT EXISTS vector;      -- 用于支持 pgvector 向量检索

-- ==========================================
-- 1. 组织架构与权限模块
-- ==========================================

-- 部门表
CREATE TABLE sys_dept (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,              -- 部门名称
    parent_id BIGINT DEFAULT 0,             -- 上级部门ID，0表示顶级
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 用户表
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,                -- 所属部门ID
    username VARCHAR(64) UNIQUE NOT NULL,   -- 登录账号
    password_hash VARCHAR(255) NOT NULL,    -- 密码哈希
    real_name VARCHAR(64),                  -- 真实姓名
    role VARCHAR(32) NOT NULL,              -- 角色：ADMIN, DISPATCHER, PROCESSOR
    status SMALLINT DEFAULT 1,              -- 状态：1启用，0禁用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'DISPATCHER', 'PROCESSOR')),
    CONSTRAINT chk_user_status CHECK (status IN (0, 1)),
    CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT
);
CREATE INDEX idx_user_dept ON sys_user(dept_id);

-- 部门邮箱配置表
CREATE TABLE dept_email_config (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    email_address VARCHAR(128) NOT NULL,
    imap_host VARCHAR(128) NOT NULL,
    imap_port INT NOT NULL DEFAULT 993,
    auth_code VARCHAR(255) NOT NULL,        -- 邮箱授权码
    last_uid BIGINT DEFAULT 0,              -- 最后拉取的邮件UID
    status SMALLINT DEFAULT 1,              -- 1启用，0停用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (email_address),
    CONSTRAINT chk_email_status CHECK (status IN (0, 1)),
    CONSTRAINT fk_email_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE CASCADE
);

-- ==========================================
-- 2. 资产库与文件版本模块
-- ==========================================

-- 部门标签库表
CREATE TABLE sys_tag (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    tag_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dept_id, tag_name),
    CONSTRAINT fk_tag_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE CASCADE
);

-- 逻辑文件主表（只存源文件、模板、导出物，不存活文档）
CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_category VARCHAR(32) NOT NULL,     -- SOURCE(源文件), TEMPLATE(模板), EXPORT(导出物)
    source_type VARCHAR(32) NOT NULL,       -- WEB, EMAIL, SYSTEM
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_doc_category CHECK (file_category IN ('SOURCE', 'TEMPLATE', 'EXPORT')),
    CONSTRAINT fk_doc_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT,
    CONSTRAINT fk_doc_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user(id) ON DELETE RESTRICT
);
CREATE INDEX idx_doc_dept_cat ON document(dept_id, file_category);

-- 物理版本表（多版本控制）
CREATE TABLE document_version (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    file_size BIGINT,
    sha256 CHAR(64),                        -- 用于文件去重
    storage_key VARCHAR(512),               -- MinIO 路径
    parse_status VARCHAR(32) DEFAULT 'UPLOADED', -- UPLOADED, PARSING, PARSED, FAILED
    is_current SMALLINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, version_no),
    CONSTRAINT chk_ver_no CHECK (version_no > 0),
    CONSTRAINT chk_ver_size CHECK (file_size >= 0),
    CONSTRAINT chk_ver_current CHECK (is_current IN (0, 1)),
    CONSTRAINT chk_ver_status CHECK (parse_status IN ('UPLOADED', 'PARSING', 'PARSED', 'FAILED')),
    CONSTRAINT fk_ver_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE
);
CREATE INDEX idx_version_doc ON document_version(document_id);

-- 文件与标签关联表
CREATE TABLE document_tag_rel (
    document_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (document_id, tag_id),
    CONSTRAINT fk_rel_tag_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    CONSTRAINT fk_rel_tag_tag FOREIGN KEY (tag_id) REFERENCES sys_tag(id) ON DELETE CASCADE
);

-- ==========================================
-- 3. 活文档模块 (类似飞书 JSON 树)
-- ==========================================

-- 可编辑文档主表
CREATE TABLE editable_doc (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_task_id BIGINT,                  -- 溯源任务（稍后加外键）
    status VARCHAR(32) DEFAULT 'DRAFT',     -- DRAFT, FINAL, ARCHIVED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_edoc_status CHECK (status IN ('DRAFT', 'FINAL', 'ARCHIVED')),
    CONSTRAINT fk_edoc_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT,
    CONSTRAINT fk_edoc_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id) ON DELETE RESTRICT
);
CREATE INDEX idx_edoc_dept ON editable_doc(dept_id);

-- 活文档版本表 (存储 JSON-DOM 树)
CREATE TABLE editable_doc_version (
    id BIGSERIAL PRIMARY KEY,
    editable_doc_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    content_json JSONB NOT NULL,            -- 统一的 JSON 树结构，内含溯源标记
    operation_type VARCHAR(32),             -- AI_GENERATE, AI_EDIT, MANUAL_EDIT
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (editable_doc_id, version_no),
    CONSTRAINT chk_edoc_ver_no CHECK (version_no > 0),
    CONSTRAINT chk_edoc_op_type CHECK (operation_type IN ('AI_GENERATE', 'AI_EDIT', 'MANUAL_EDIT')),
    CONSTRAINT fk_edoc_ver_doc FOREIGN KEY (editable_doc_id) REFERENCES editable_doc(id) ON DELETE CASCADE,
    CONSTRAINT fk_edoc_ver_user FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
);

-- ==========================================
-- 4. 文档解析与向量模块
-- ==========================================

-- 文档解析块表
CREATE TABLE document_block (
    id BIGSERIAL PRIMARY KEY,
    document_version_id BIGINT NOT NULL,
    block_index INT NOT NULL,
    block_type VARCHAR(32) NOT NULL,        -- TITLE, PARAGRAPH, TABLE
    content TEXT NOT NULL,
    table_json JSONB,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_block_type CHECK (block_type IN ('TITLE', 'PARAGRAPH', 'TABLE')),
    CONSTRAINT fk_block_ver FOREIGN KEY (document_version_id) REFERENCES document_version(id) ON DELETE CASCADE
);
CREATE INDEX idx_block_version ON document_block(document_version_id);
CREATE INDEX idx_block_embedding ON document_block USING hnsw (embedding vector_cosine_ops);

-- ==========================================
-- 5. 智能抽取与任务模块
-- ==========================================

-- 抽取规则模板表
CREATE TABLE extract_schema (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    prompt_text TEXT,
    schema_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schema_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT,
    CONSTRAINT fk_schema_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id) ON DELETE RESTRICT
);

-- 处理任务表
CREATE TABLE task (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    assignee_id BIGINT,                     -- 可为空（待分配）
    task_type VARCHAR(32) NOT NULL,         -- EXTRACT, FILL, EDIT_DOC
    schema_id BIGINT,
    template_doc_id BIGINT,
    status VARCHAR(32) DEFAULT 'PENDING',   -- PENDING, PROCESSING, COMPLETED, FAILED
    result_doc_id BIGINT,                   -- 若产出物是物理文件
    result_editable_doc_id BIGINT,          -- 若产出物是活文档
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT chk_task_type CHECK (task_type IN ('EXTRACT', 'FILL', 'EDIT_DOC')),
    CONSTRAINT chk_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT fk_task_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_task_schema FOREIGN KEY (schema_id) REFERENCES extract_schema(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_template FOREIGN KEY (template_doc_id) REFERENCES document(id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_res_doc FOREIGN KEY (result_doc_id) REFERENCES document(id) ON DELETE SET NULL,
    CONSTRAINT fk_task_res_edoc FOREIGN KEY (result_editable_doc_id) REFERENCES editable_doc(id) ON DELETE SET NULL
);
CREATE INDEX idx_task_dept_status ON task(dept_id, status);
CREATE INDEX idx_task_assignee ON task(assignee_id);

-- 补充：把 editable_doc 的 source_task_id 外键加上
ALTER TABLE editable_doc 
ADD CONSTRAINT fk_edoc_task FOREIGN KEY (source_task_id) REFERENCES task(id) ON DELETE SET NULL;

-- 任务与源文件关联表
CREATE TABLE task_document_rel (
    task_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, document_id),
    CONSTRAINT fk_tdr_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_tdr_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tdr_ver FOREIGN KEY (document_version_id) REFERENCES document_version(id) ON DELETE RESTRICT
);

-- 字段级抽取结果表
CREATE TABLE extract_result (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    field_value TEXT,
    confidence DECIMAL(5,4),
    evidence_block_id BIGINT,
    is_modified SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_res_confidence CHECK (confidence >= 0.0000 AND confidence <= 1.0000),
    CONSTRAINT chk_res_modified CHECK (is_modified IN (0, 1)),
    CONSTRAINT fk_res_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_res_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE RESTRICT,
    CONSTRAINT fk_res_block FOREIGN KEY (evidence_block_id) REFERENCES document_block(id) ON DELETE SET NULL
);
CREATE INDEX idx_result_task ON extract_result(task_id);

-- ==========================================
-- 6. 邮件暂存与审计日志模块
-- ==========================================

-- 邮件附件待确认暂存池表
CREATE TABLE email_pending (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    email_uid BIGINT NOT NULL,
    sender VARCHAR(128) NOT NULL,
    subject VARCHAR(255),
    attachment_name VARCHAR(255) NOT NULL,
    attachment_size BIGINT,
    temp_storage_key VARCHAR(512) NOT NULL,
    suggested_document_id BIGINT,
    suggested_pending_id BIGINT,
    status VARCHAR(32) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_email_pend_status CHECK (status IN ('PENDING', 'ACCEPTED', 'IGNORED')),
    CONSTRAINT fk_pend_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE CASCADE
);

-- 任务流转日志表
CREATE TABLE task_log (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    operator_id BIGINT,
    action VARCHAR(64) NOT NULL,
    detail JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_log_user FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL
);
CREATE INDEX idx_log_task ON task_log(task_id);
-- =====================================================================
-- 003_db_hardening.sql —— v3 结构检修（增量脚本）
--
-- 依据：sql/diagnostics/01_漏洞实证.sql 的 T1–T15 实测结果（全部写入成功）
-- 前提：002_schema_v3.sql 已生效
-- 纪律：不修改已执行的 001/002，所有变更走本文件
-- 特性：幂等（DROP ... IF EXISTS 后重建），可在任意环境重复执行
--
-- 执行：
--   docker exec -i docai-postgres psql -U docai -d docai -v ON_ERROR_STOP=1 \
--       -f - < sql/init/003_db_hardening.sql
-- =====================================================================

BEGIN;

-- =====================================================================
-- 1. 补齐复合外键所需的 (id, dept_id) 唯一键
--
--    背景：v3 的租户隔离依赖「复合外键 (fk_id, dept_id)」，但被引用表必须
--    先有 UNIQUE(id, dept_id)。002 漏给了 dept_email_config 与 document_block，
--    导致引用它们的外键只能退化为单列，隔离形同虚设（见第 2 节）。
--    代价：两表各多一个唯一索引，写入多一次索引维护（current: 空表，无影响）。
-- =====================================================================
ALTER TABLE dept_email_config DROP CONSTRAINT IF EXISTS uq_emailcfg_id_dept;
ALTER TABLE dept_email_config ADD  CONSTRAINT uq_emailcfg_id_dept UNIQUE (id, dept_id);

ALTER TABLE document_block DROP CONSTRAINT IF EXISTS uq_block_id_dept;
ALTER TABLE document_block ADD  CONSTRAINT uq_block_id_dept UNIQUE (id, dept_id);

-- =====================================================================
-- 2. 三个单列外键 → 复合租户外键（修 T1 / T2 / T3）
-- =====================================================================

-- 2.1 document_version.document_id：部门B 的版本行可以挂到部门A 的文件上（T1）
ALTER TABLE document_version DROP CONSTRAINT IF EXISTS document_version_document_id_fkey;
ALTER TABLE document_version DROP CONSTRAINT IF EXISTS fk_ver_doc;
ALTER TABLE document_version ADD  CONSTRAINT fk_ver_doc
    FOREIGN KEY (document_id, dept_id) REFERENCES document (id, dept_id) ON DELETE CASCADE;

-- 2.2 email_inbox.config_id：部门B 的待确认附件可以用部门A 的邮箱配置拉取（T2）
ALTER TABLE email_inbox DROP CONSTRAINT IF EXISTS email_inbox_config_id_fkey;
ALTER TABLE email_inbox DROP CONSTRAINT IF EXISTS fk_inbox_config;
ALTER TABLE email_inbox ADD  CONSTRAINT fk_inbox_config
    FOREIGN KEY (config_id, dept_id) REFERENCES dept_email_config (id, dept_id) ON DELETE CASCADE;

-- 2.3 extract_result.evidence_block_id：部门B 的结果可以引用部门A 的证据块（T3）
--     注：PG15+ 支持「列级 SET NULL」，只置空 evidence_block_id 而保留 dept_id，
--     解决了 db-design-v3.md §4.6 「复合外键 + NOT NULL 租户列无法 SET NULL」的顾虑。
ALTER TABLE extract_result DROP CONSTRAINT IF EXISTS fk_res_block;
ALTER TABLE extract_result ADD  CONSTRAINT fk_res_block
    FOREIGN KEY (evidence_block_id, dept_id) REFERENCES document_block (id, dept_id)
    ON DELETE SET NULL (evidence_block_id);

-- =====================================================================
-- 3. 业务表「作者列」补外键（修 T11：created_by 可指向不存在的用户）
--    仅业务表补；日志/审计表刻意不补（避免历史操作人阻塞账号物理删除）。
-- =====================================================================
ALTER TABLE sys_tag DROP CONSTRAINT IF EXISTS fk_tag_creator;
ALTER TABLE sys_tag ADD  CONSTRAINT fk_tag_creator FOREIGN KEY (created_by, dept_id)
    REFERENCES sys_user (id, dept_id) ON DELETE SET NULL (created_by);

ALTER TABLE document_version DROP CONSTRAINT IF EXISTS fk_ver_creator;
ALTER TABLE document_version ADD  CONSTRAINT fk_ver_creator FOREIGN KEY (created_by, dept_id)
    REFERENCES sys_user (id, dept_id) ON DELETE SET NULL (created_by);

ALTER TABLE document_tag_rel DROP CONSTRAINT IF EXISTS fk_dtr_creator;
ALTER TABLE document_tag_rel ADD  CONSTRAINT fk_dtr_creator FOREIGN KEY (created_by, dept_id)
    REFERENCES sys_user (id, dept_id) ON DELETE SET NULL (created_by);

ALTER TABLE extract_result DROP CONSTRAINT IF EXISTS fk_res_modifier;
ALTER TABLE extract_result ADD  CONSTRAINT fk_res_modifier FOREIGN KEY (modified_by, dept_id)
    REFERENCES sys_user (id, dept_id) ON DELETE SET NULL (modified_by);

-- =====================================================================
-- 4. append-only 真正落地（修 T12：审计日志可被 UPDATE / DELETE）
--    002 只在注释里写了「禁止 UPDATE/DELETE」，数据库并未拦截。
-- =====================================================================
CREATE OR REPLACE FUNCTION trg_forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% 为 append-only 审计表，禁止 %', TG_TABLE_NAME, TG_OP
        USING ERRCODE = '42501',
              HINT = '归档请按保留期整表导出，由 DBA 在维护窗口处理';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_task_log ON task_log;
CREATE TRIGGER trg_no_update_task_log BEFORE UPDATE OR DELETE ON task_log
    FOR EACH ROW EXECUTE FUNCTION trg_forbid_mutation();
DROP TRIGGER IF EXISTS trg_no_truncate_task_log ON task_log;
CREATE TRIGGER trg_no_truncate_task_log BEFORE TRUNCATE ON task_log
    FOR EACH STATEMENT EXECUTE FUNCTION trg_forbid_mutation();

DROP TRIGGER IF EXISTS trg_no_update_audit ON sys_audit_log;
CREATE TRIGGER trg_no_update_audit BEFORE UPDATE OR DELETE ON sys_audit_log
    FOR EACH ROW EXECUTE FUNCTION trg_forbid_mutation();
DROP TRIGGER IF EXISTS trg_no_truncate_audit ON sys_audit_log;
CREATE TRIGGER trg_no_truncate_audit BEFORE TRUNCATE ON sys_audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION trg_forbid_mutation();

-- =====================================================================
-- 5. 部门树防环（修 T13：A→B→A 可写入，递归查询会失控）
--    ck_dept_not_self 只挡住了「自己是自己的上级」一步，挡不住多跳成环。
-- =====================================================================
CREATE OR REPLACE FUNCTION trg_dept_no_cycle() RETURNS trigger AS $$
DECLARE
    cur  BIGINT;
    hops INT := 0;
BEGIN
    -- parent_id = 0 由 trg_dept_parent_norm 归一为 NULL，此处直接放行
    IF NEW.parent_id IS NULL OR NEW.parent_id = 0 THEN
        RETURN NEW;
    END IF;

    cur := NEW.parent_id;
    WHILE cur IS NOT NULL AND hops < 1000 LOOP
        IF cur = NEW.id THEN
            RAISE EXCEPTION '部门树不允许成环：id=% 的上级链路回到了自身', NEW.id
                USING ERRCODE = '23514';
        END IF;
        SELECT parent_id INTO cur FROM sys_dept WHERE id = cur;
        hops := hops + 1;
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_dept_no_cycle ON sys_dept;
CREATE TRIGGER trg_dept_no_cycle BEFORE INSERT OR UPDATE OF parent_id ON sys_dept
    FOR EACH ROW EXECUTE FUNCTION trg_dept_no_cycle();

-- =====================================================================
-- 6. 抽取结果必须引用「本任务的源文件」（修 T4）
--    外键只保证 task/document 各自同租户，保证不了「文档在该任务的清单里」，
--    会出现结果挂到无关文件上、证据链错乱。
--    【调用约束】应用层须先写 task_document_rel，再写 extract_result。
-- =====================================================================
CREATE OR REPLACE FUNCTION trg_res_doc_in_task() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM task_document_rel r
        WHERE r.task_id = NEW.task_id
          AND r.document_id = NEW.document_id
          AND r.dept_id = NEW.dept_id
    ) THEN
        RAISE EXCEPTION '抽取结果的源文件 % 未挂在该任务 % 的源文件清单中',
            NEW.document_id, NEW.task_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_res_doc_in_task ON extract_result;
CREATE TRIGGER trg_res_doc_in_task BEFORE INSERT OR UPDATE OF task_id, document_id ON extract_result
    FOR EACH ROW EXECUTE FUNCTION trg_res_doc_in_task();

-- =====================================================================
-- 7. 状态与时间戳/附属字段自洽（修 T5–T10、T15）
--    枚举 CHECK 只管「值合法」，管不了「状态与字段是否配套」。
-- =====================================================================

-- 7.1 任务终态必须有结束时间
ALTER TABLE task DROP CONSTRAINT IF EXISTS ck_task_terminal_time;
ALTER TABLE task ADD  CONSTRAINT ck_task_terminal_time
    CHECK (status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') OR completed_at IS NOT NULL);

-- 7.2 失败必须有原因
ALTER TABLE task DROP CONSTRAINT IF EXISTS ck_task_failed_msg;
ALTER TABLE task ADD  CONSTRAINT ck_task_failed_msg
    CHECK (status <> 'FAILED' OR error_msg IS NOT NULL);

-- 7.3 完成必须 100%
ALTER TABLE task DROP CONSTRAINT IF EXISTS ck_task_done_progress;
ALTER TABLE task ADD  CONSTRAINT ck_task_done_progress
    CHECK (status <> 'COMPLETED' OR progress = 100);

-- 7.4 邮件附件：认领必须有落地文件；驳回必须有原因
ALTER TABLE email_inbox DROP CONSTRAINT IF EXISTS ck_inbox_claimed_doc;
ALTER TABLE email_inbox ADD  CONSTRAINT ck_inbox_claimed_doc
    CHECK (status <> 'CLAIMED' OR document_id IS NOT NULL);

ALTER TABLE email_inbox DROP CONSTRAINT IF EXISTS ck_inbox_rejected_reason;
ALTER TABLE email_inbox ADD  CONSTRAINT ck_inbox_rejected_reason
    CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL);

-- 7.5 文件：解析完成必须有完成时间；解析失败必须有错误信息；人工上传必须有上传人
ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_doc_parsed_at;
ALTER TABLE document ADD  CONSTRAINT ck_doc_parsed_at
    CHECK (parse_status <> 'PARSED' OR parsed_at IS NOT NULL);

ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_doc_failed_err;
ALTER TABLE document ADD  CONSTRAINT ck_doc_failed_err
    CHECK (parse_status <> 'FAILED' OR parse_error IS NOT NULL);

ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_doc_uploader_required;
ALTER TABLE document ADD  CONSTRAINT ck_doc_uploader_required
    CHECK (uploader_type <> 'USER' OR uploader_id IS NOT NULL);

-- 7.6 表格块必须有结构化内容（便于回填与复核，避免只有纯文本）
ALTER TABLE document_block DROP CONSTRAINT IF EXISTS ck_block_table_json;
ALTER TABLE document_block ADD  CONSTRAINT ck_block_table_json
    CHECK (block_type <> 'TABLE' OR table_json IS NOT NULL);

-- =====================================================================
-- 8. IMAP 幂等：补 UIDVALIDITY
--    问题：IMAP 的 UID 只在同一 UIDVALIDITY 内单调，邮箱重建/服务器迁移后
--    UID 会从头重用。原唯一键 (config_id, message_uid, attachment_index)
--    会把「新邮件」误判为「已入队」直接丢弃 —— 表现是静默漏收附件。
-- =====================================================================
ALTER TABLE dept_email_config ADD COLUMN IF NOT EXISTS uid_validity BIGINT NOT NULL DEFAULT 0;
ALTER TABLE email_inbox       ADD COLUMN IF NOT EXISTS uid_validity BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN dept_email_config.uid_validity IS 'IMAP UIDVALIDITY；变化即代表 UID 空间重置，需清零 last_uid 并全量重扫';
COMMENT ON COLUMN email_inbox.uid_validity       IS '入队时邮箱的 UIDVALIDITY，与 message_uid 共同构成幂等键';

DROP INDEX IF EXISTS uq_inbox_uid;
CREATE UNIQUE INDEX uq_inbox_uid
    ON email_inbox (config_id, uid_validity, message_uid, attachment_index);

-- =====================================================================
-- 9. 版本表补存储桶
--    问题：document_version 只有 storage_key，隐含「与 document 同桶」。
--    一旦文件跨桶迁移或换存储后端，历史版本将无法定位。
-- =====================================================================
ALTER TABLE document_version ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(64) NOT NULL DEFAULT 'docai';
COMMENT ON COLUMN document_version.storage_bucket IS '版本对象所在桶；默认与 document 一致，迁移时逐版本写入';

-- =====================================================================
-- 10. 同部门内邮箱不重复（修 T14）
--     登录名全局唯一，但 email 完全没约束，会出现同部门重复账号/重复通知。
-- =====================================================================
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_email ON sys_user (dept_id, lower(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL;

-- =====================================================================
-- 11. 向量检索：开启 HNSW 迭代扫描
--     问题：hnsw 默认「先取 topN 再按 dept_id 过滤」，租户多、单租户块少时
--     召回率会明显下降（多租户 RAG 的典型坑）。relaxed_order 让索引在过滤后继续扫描。
-- =====================================================================
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET hnsw.iterative_scan = %L',
                   current_database(), 'relaxed_order');
END $$;

COMMIT;

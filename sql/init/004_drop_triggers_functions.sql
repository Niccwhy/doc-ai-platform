-- =====================================================================
-- 004_drop_triggers_functions.sql
--
-- 目的：移除数据库层的业务规则实现（触发器 + PL/pgSQL 函数），
--       规则改由 Service 层承担。执行后库中只剩表、约束、索引。
--
-- 影响（需应用层配合，见 docs/db-hardening-2026-11-03.md 附录）：
--   1) parent_id = 0 不再被自动归一为 NULL -> 代码需直接写 NULL
--   2) updated_at 不再自动维护          -> 由应用写入
--   3) task_log / sys_audit_log 不再防篡改 -> 靠应用只写不改
--   4) 部门树、结果归属不再由库校验      -> 靠 Service 校验
-- =====================================================================

BEGIN;

-- 1. 触发器（18 个）
DROP TRIGGER IF EXISTS trg_dept_parent_norm          ON sys_dept;
DROP TRIGGER IF EXISTS trg_dept_no_cycle             ON sys_dept;
DROP TRIGGER IF EXISTS trg_touch_sys_dept            ON sys_dept;
DROP TRIGGER IF EXISTS trg_touch_sys_user            ON sys_user;
DROP TRIGGER IF EXISTS trg_touch_dept_email_config   ON dept_email_config;
DROP TRIGGER IF EXISTS trg_touch_email_whitelist     ON email_whitelist;
DROP TRIGGER IF EXISTS trg_touch_sys_tag             ON sys_tag;
DROP TRIGGER IF EXISTS trg_touch_document            ON document;
DROP TRIGGER IF EXISTS trg_touch_email_inbox         ON email_inbox;
DROP TRIGGER IF EXISTS trg_touch_extract_schema      ON extract_schema;
DROP TRIGGER IF EXISTS trg_touch_template_field_mapping ON template_field_mapping;
DROP TRIGGER IF EXISTS trg_touch_task                ON task;
DROP TRIGGER IF EXISTS trg_touch_extract_result      ON extract_result;
DROP TRIGGER IF EXISTS trg_res_doc_in_task           ON extract_result;
DROP TRIGGER IF EXISTS trg_no_update_task_log        ON task_log;
DROP TRIGGER IF EXISTS trg_no_truncate_task_log      ON task_log;
DROP TRIGGER IF EXISTS trg_no_update_audit           ON sys_audit_log;
DROP TRIGGER IF EXISTS trg_no_truncate_audit         ON sys_audit_log;

-- 2. 函数（5 个）
DROP FUNCTION IF EXISTS trg_touch_updated_at();
DROP FUNCTION IF EXISTS trg_dept_parent_zero_to_null();
DROP FUNCTION IF EXISTS trg_forbid_mutation();
DROP FUNCTION IF EXISTS trg_dept_no_cycle();
DROP FUNCTION IF EXISTS trg_res_doc_in_task();

COMMIT;

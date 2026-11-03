-- =====================================================================
-- 数据库漏洞实证脚本（只读：全程事务 + ROLLBACK，不留任何数据）
--
-- 用法：
--   docker exec -i docai-postgres psql -U docai -d docai -f - < 本文件
--
-- 判读：
--   输出 INSERT 0 1 / UPDATE 1  => 漏洞存在（数据库未拦截）
--   输出 ERROR: ...             => 已被保护
-- =====================================================================
\set ON_ERROR_STOP off

BEGIN;

-- ---------- 测试夹具：另建一个部门 B 及其文件、任务 ----------
INSERT INTO sys_dept (id, name, status) VALUES (999, '体检-部门B', 'ACTIVE');
INSERT INTO document (id, dept_id, uploader_type, file_name, file_type, file_category,
                      file_size, sha256, storage_key)
VALUES (999, 999, 'SYSTEM', 'b.docx', 'docx', 'SOURCE', 1, repeat('b', 64), 'k-b');
INSERT INTO task (id, dept_id, task_type, status) VALUES (999, 999, 'EXTRACT', 'PENDING');
-- 部门 A（真实部门 1）的任务，供一致性用例
INSERT INTO task (id, dept_id, task_type, status) VALUES (998, 1, 'EXTRACT', 'PENDING');
-- 部门B 的任务已挂部门B 的文件（使 T3 精确落在「证据跨租户」而非「文件未挂任务」）
INSERT INTO task_document_rel (task_id, document_id, dept_id) VALUES (999, 999, 999);
-- 部门 A（真实部门 1）的一个块，供跨租户引用
INSERT INTO document_block (id, dept_id, document_id, block_index, block_type, content)
VALUES (999, 1, (SELECT min(id) FROM document WHERE dept_id = 1), 9000, 'PARAGRAPH', 'block-A');

\echo ''
\echo '### T1 document_version：部门B 的版本行指向部门A 的文件'
SAVEPOINT t1;
INSERT INTO document_version (document_id, dept_id, version_no, storage_key, file_size, sha256)
VALUES ((SELECT min(id) FROM document WHERE dept_id = 1), 999, 77, 'k', 1, repeat('c', 64));
ROLLBACK TO t1;

\echo '### T2 email_inbox：部门B 的待确认附件 用 部门A 的邮箱配置'
SAVEPOINT t0;
INSERT INTO dept_email_config (id, dept_id, email_address, imap_host, auth_code_enc)
VALUES (999, 1, 'a@corp.com', 'imap.corp.com', 'enc');
RELEASE SAVEPOINT t0;
SAVEPOINT t2;
INSERT INTO email_inbox (dept_id, config_id, message_uid, attachment_index,
                         from_addr, attachment_name, file_size, sha256, storage_key)
VALUES (999, 999, 1, 0, 'x@y.com', 'a.docx', 1, repeat('d', 64), 'k');
ROLLBACK TO t2;

\echo '### T3 extract_result：部门B 的结果 引用 部门A 的 block（证据跨租户）'
SAVEPOINT t3;
INSERT INTO extract_result (dept_id, task_id, document_id, field_key, field_value, evidence_block_id)
VALUES (999, 999, 999, 'amount', '100', 999);
ROLLBACK TO t3;

\echo '### T4 extract_result：结果所属文件 不在该任务的源文件清单里（业务一致性）'
SAVEPOINT t4;
INSERT INTO extract_result (dept_id, task_id, document_id, field_key, field_value)
VALUES (1, 998, (SELECT min(id) FROM document WHERE dept_id = 1), 'amount', '100');
ROLLBACK TO t4;

\echo '### T5 task：状态 COMPLETED 但 completed_at 为空（progress=100 以隔离该检查）'
SAVEPOINT t5;
INSERT INTO task (dept_id, task_type, status, progress, completed_at) VALUES (1, 'FILL', 'COMPLETED', 100, NULL);
ROLLBACK TO t5;

\echo '### T6 task：状态 FAILED 但 error_msg 为空'
SAVEPOINT t6;
INSERT INTO task (dept_id, task_type, status, error_msg) VALUES (1, 'FILL', 'FAILED', NULL);
ROLLBACK TO t6;

\echo '### T7 email_inbox：状态 REJECTED 但 reject_reason 为空'
SAVEPOINT t7;
INSERT INTO email_inbox (dept_id, config_id, message_uid, attachment_index, from_addr,
                         attachment_name, file_size, sha256, storage_key, status, reject_reason)
VALUES (1, 999, 2, 0, 'x@y.com', 'a.docx', 1, repeat('e', 64), 'k', 'REJECTED', NULL);
ROLLBACK TO t7;

\echo '### T8 email_inbox：状态 CLAIMED 但 document_id 为空（认领了却没入库）'
SAVEPOINT t8;
INSERT INTO email_inbox (dept_id, config_id, message_uid, attachment_index, from_addr,
                         attachment_name, file_size, sha256, storage_key, status, document_id)
VALUES (1, 999, 3, 0, 'x@y.com', 'a.docx', 1, repeat('f', 64), 'k', 'CLAIMED', NULL);
ROLLBACK TO t8;

\echo '### T9 document：parse_status=PARSED 但 parsed_at 为空'
SAVEPOINT t9;
INSERT INTO document (dept_id, uploader_type, file_name, file_type, file_category,
                      file_size, sha256, storage_key, parse_status, parsed_at)
VALUES (1, 'SYSTEM', 'c.docx', 'docx', 'SOURCE', 1, repeat('1', 64), 'k', 'PARSED', NULL);
ROLLBACK TO t9;

\echo '### T10 document_block：block_type=TABLE 但 table_json 为空'
SAVEPOINT t10;
INSERT INTO document_block (dept_id, document_id, block_index, block_type, content, table_json)
VALUES (1, (SELECT min(id) FROM document WHERE dept_id = 1), 9001, 'TABLE', 'x', NULL);
ROLLBACK TO t10;

\echo '### T11 sys_tag：created_by 指向不存在的用户（该列无外键）'
SAVEPOINT t11;
INSERT INTO sys_tag (dept_id, tag_name, created_by) VALUES (1, '体检标签', 888888);
ROLLBACK TO t11;

\echo '### T12a task_log：篡改审计日志（UPDATE）'
SAVEPOINT t12;
INSERT INTO task_log (dept_id, task_id, action) VALUES (1, 998, 'CREATE');
ROLLBACK TO t12;

SAVEPOINT t12a;
INSERT INTO task_log (id, dept_id, task_id, action) VALUES (997, 1, 998, 'CREATE');
UPDATE task_log SET action = 'TAMPERED' WHERE id = 997;
ROLLBACK TO t12a;

\echo '### T12b task_log：销毁审计日志（DELETE）'
SAVEPOINT t12b;
INSERT INTO task_log (id, dept_id, task_id, action) VALUES (997, 1, 998, 'CREATE');
DELETE FROM task_log WHERE id = 997;
ROLLBACK TO t12b;

\echo '### T13 sys_dept：部门树成环（999 的上级→1，再把 1 的上级→999）'
SAVEPOINT t13;
UPDATE sys_dept SET parent_id = 1 WHERE id = 999;
UPDATE sys_dept SET parent_id = 999 WHERE id = 1;
ROLLBACK TO t13;

\echo '### T14 sys_user：同部门同邮箱重复'
SAVEPOINT t14;
INSERT INTO sys_user (dept_id, username, password_hash, role, email) VALUES (1, 'u1', 'x', 'PROCESSOR', 'dup@corp.com');
INSERT INTO sys_user (dept_id, username, password_hash, role, email) VALUES (1, 'u2', 'x', 'PROCESSOR', 'dup@corp.com');
ROLLBACK TO t14;

\echo '### T15 task：progress 在终态仍为 0（终态与进度不一致）'
SAVEPOINT t15;
INSERT INTO task (dept_id, task_type, status, progress, completed_at)
VALUES (1, 'FILL', 'COMPLETED', 0, now());
ROLLBACK TO t15;

ROLLBACK;
\echo ''
\echo '=== 全部回滚，未写入任何数据 ==='

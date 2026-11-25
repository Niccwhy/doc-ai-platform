# 数据库变更日志

## 2026-11-23 - 部门内文件 SHA-256 去重约束

- 为 `document_version` 增加 `dept_id` 字段，并通过外键关联 `sys_dept.id`。
- 创建部分唯一索引 `uk_document_version_dept_sha256_current`，约束同一部门的当前版本不能重复保存相同 SHA-256。
- 不同部门允许保存相同 SHA-256 的文件。
- 新增查询索引 `idx_document_version_dept_sha256`，用于部门内去重查询。
- 迁移脚本：`sql/02_document_dedup_and_manual_email_import.sql`

## 2026-11-25 - 手动邮件发现与附件选择导入

- `dept_email_config` 增加 `uid_validity`，与 `last_uid` 一起跟踪 IMAP INBOX 游标；迁移时重置旧自动拉取游标，以便建立新邮件元信息记录。
- 新增 `email_message` 保存邮件元信息、正文和处理状态，按邮箱配置、UIDVALIDITY、邮件 UID 保证幂等。
- 新增 `email_attachment` 保存附件元信息、MIME 部件标识、逐附件状态及导入后的 `document_version_id`。
- 新流程发现邮件时只写入邮件和附件元数据；用户选择后才读取附件内容、上传 MinIO 并创建 `document`、`document_version`。
- 旧 `email_pending` 表保留以避免破坏已初始化数据库，但新邮件代码不再使用。
- 迁移脚本：`sql/02_document_dedup_and_manual_email_import.sql`

-- =============================================================
-- 002：向量维度从 1024 调整为 1536（通义 text-embedding-v3）
--
-- 适用场景：数据库卷已存在（001_schema.sql 已执行过）时的手动升级。
-- 全新初始化时 001 已是 1536 维，无需执行本脚本。
--
-- 执行方式：
--   docker exec -i docai-postgres psql -U docai -d docai < sql/init/002_alter_embedding_1536.sql
--
-- 注意：维度变更前需清空已有向量，否则转换会失败。
-- =============================================================

UPDATE doc_chunk SET embedding = NULL;

ALTER TABLE doc_chunk ALTER COLUMN embedding TYPE vector(1536);

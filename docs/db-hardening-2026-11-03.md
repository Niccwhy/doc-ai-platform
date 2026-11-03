# 数据库结构体检与检修报告

> 对象库：`docai`（PostgreSQL 16.15 / pgvector 0.8.7 / pg_trgm）
> 基线：`sql/init/002_schema_v3.sql`（18 表）
> 检修脚本：`sql/init/003_db_hardening.sql`
> 实证脚本：`sql/diagnostics/01_漏洞实证.sql`
> 执行时间：2026-11-03 16:30（阶段 2-1 资产入库完成后）
> 备份：`data/backup/postgres-v3-pre-hardening-20261103.sql`（69 KB，含 `--clean`）

---

## 一、体检方法

不止读 DDL，而是**用数据库自己来证明**：对每一条怀疑的规则，在一个 `SAVEPOINT` 里尝试写入违规数据，观察是「写入成功（漏洞）」还是「被拒（已保护）」，最后 `ROLLBACK` 保证零残留。

15 个用例覆盖：跨租户引用、审计篡改、树形成环、状态与字段不自洽、悬空引用。检修前后各跑一次，形成对照。

---

## 二、结论摘要

| 指标 | 检修前 | 检修后 | 说明 |
|---|---|---|---|
| 业务表 | 18 | 18 | 无新增表 |
| 外键 | 32 | **36** | 3 个单列键升级为复合租户键，新增 4 个作者列外键 |
| CHECK 约束 | 27 | **36** | 新增 9 条状态自洽约束 |
| UNIQUE 约束 | 5 | **7** | 补齐 2 个复合外键的被引用前提 |
| 索引 | 65 | **68** | 净增 3（+2 唯一键 +1 邮箱唯一） |
| 触发器 | 12 | **18** | +4 审计防篡改、+1 树防环、+1 结果归属校验 |
| 实证用例通过（被拦截） | 0 / 15 | **15 / 15** | 全部从「写入成功」变为「明确报错」 |

**一句话**：DDL 的建模骨架没有问题，问题集中在「约束只写了一半」——复合外键缺被引用的唯一键、审计 append-only 只写在注释里、状态枚举只管值合法不管字段配套。

---

## 三、发现的问题与修复措施

### P0（安全 / 正确性，可导致数据越权或不可追溯）

#### P0-1 版本链可跨租户挂载

- **问题**：`document_version.document_id` 是单列外键，`dept_id` 是另一个独立外键。两个约束各自成立，但组合起来无意义——可以写出「部门 B 的版本行指向部门 A 的文件」。
- **证据**：T1 写入成功（`INSERT 0 1`）。
- **修复**：改为复合外键 `(document_id, dept_id) → document(id, dept_id)`。
- **结果**：`violates foreign key constraint "fk_ver_doc"` ✅

#### P0-2 邮件队列可盗用他部门邮箱配置

- **问题**：`email_inbox.config_id` 单列外键。部门 B 的附件入队时可以挂部门 A 的 IMAP 配置，等于用别人的邮箱身份拉别人的信。
- **根因**：`dept_email_config` 缺 `UNIQUE(id, dept_id)`，复合外键无从建立。
- **证据**：T2 写入成功。
- **修复**：补 `uq_emailcfg_id_dept`，改复合外键 `(config_id, dept_id)`。
- **结果**：`violates foreign key constraint "fk_inbox_config"` ✅

#### P0-3 证据链可跨租户引用

- **问题**：`extract_result.evidence_block_id` 单列外键，部门 B 的抽取结果可以引用部门 A 的原文块。证据溯源是本项目的评分点，串租户等于证据失效。
- **原设计顾虑**：`db-design-v3.md §4.6` 认为「复合外键 + `dept_id NOT NULL` 时 `ON DELETE SET NULL` 不可用」，因此这条外键被留成单列。
- **该顾虑已被 PostgreSQL 15+ 解除**：支持**列级 SET NULL** `ON DELETE SET NULL (evidence_block_id)`，只置空外键列、保留 `dept_id`。本库 PG16 已实测验证。
- **修复**：补 `uq_block_id_dept`，改复合外键 + 列级 SET NULL。
- **结果**：`violates foreign key constraint "fk_res_block"`，且块被删时仅 `evidence_block_id` 置空，`dept_id` 不变 ✅

#### P0-4 审计日志「append-only」只是注释

- **问题**：`db-design-v3.md` 与表注释都声明 `task_log` / `sys_audit_log` 禁止 UPDATE/DELETE，但数据库没有任何强制。审计表的全部价值在于「改不了」，能改就等于没有。
- **证据**：T12a `UPDATE 1` 成功、T12b `DELETE 1` 成功。
- **修复**：`BEFORE UPDATE OR DELETE`（行级）+ `BEFORE TRUNCATE`（语句级）触发器，抛 `42501`。
- **结果**：UPDATE → `task_log 为 append-only 审计表，禁止 UPDATE`；DELETE → `... 禁止 DELETE` ✅；INSERT 仍放行。

#### P0-5 部门树可以成环

- **问题**：`ck_dept_not_self` 只挡住「自己是自己的上级」，挡不住多跳成环。一旦成环，按部门递归汇总、递归删除、菜单树渲染全部死循环。
- **证据**：T13 中 `999→1` 后把 `1→999`，`UPDATE 1` 成功。
- **修复**：`BEFORE INSERT OR UPDATE OF parent_id` 触发器，沿上级链上溯，命中自身即拒绝。
- **结果**：`部门树不允许成环：id=1 的上级链路回到了自身` ✅（合法的单向上挂仍通过）

### P1（数据一致性，会让业务读出错误结论）

#### P1-1 抽取结果的文件可以不属于该任务

- **问题**：外键只保证了 `task` 与 `document` 各自同租户，没保证「这份文档在该任务的源文件清单里」。结果可以挂到任意同部门文件上，复核页双屏对照会张冠李戴，准确率统计也会失真。
- **证据**：T4 写入成功（任务的源文件清单为空，仍写入结果）。
- **修复**：`BEFORE INSERT OR UPDATE OF task_id, document_id` 触发器，校验 `task_document_rel` 中存在对应行。
- **结果**：`抽取结果的源文件 1 未挂在该任务 998 的源文件清单中` ✅

#### P1-2 状态枚举只管「值合法」，不管「字段配套」

- **问题**：枚举 CHECK 能保证 `status` 是六个合法值之一，但保证不了 `COMPLETED` 有完成时间、`FAILED` 有错误原因。现状可以写出「已完成但没时间」「失败了但不知道为什么」「认领了但没入库」这类自相矛盾的行，前端展示与 SLA 统计都会出错。
- **证据**：T5/T6/T7/T8/T9/T10/T15 七项全部写入成功。
- **修复**：新增 9 条 CHECK：

| 表 | 约束 | 规则 |
|---|---|---|
| `task` | `ck_task_terminal_time` | 终态（完成/失败/取消）必须 `completed_at` |
| `task` | `ck_task_failed_msg` | `FAILED` 必须有 `error_msg` |
| `task` | `ck_task_done_progress` | `COMPLETED` 必须 `progress = 100` |
| `email_inbox` | `ck_inbox_claimed_doc` | `CLAIMED` 必须 `document_id` |
| `email_inbox` | `ck_inbox_rejected_reason` | `REJECTED` 必须 `reject_reason` |
| `document` | `ck_doc_parsed_at` | `PARSED` 必须 `parsed_at` |
| `document` | `ck_doc_failed_err` | `FAILED` 必须 `parse_error` |
| `document` | `ck_doc_uploader_required` | `uploader_type='USER'` 必须有 `uploader_id` |
| `document_block` | `ck_block_table_json` | `TABLE` 块必须有 `table_json` |

- **结果**：7 项全部拦截，报错精确到约束名 ✅

#### P1-3 业务表的作者列没有外键

- **问题**：`sys_tag.created_by`、`document_version.created_by`、`document_tag_rel.created_by`、`extract_result.modified_by` 都是「谁干的」这类字段，却完全没有外键约束。
- **证据**：T11 写入 `created_by = 888888`（不存在的用户）成功。
- **修复**：4 个复合外键 `(created_by, dept_id) → sys_user(id, dept_id)`，`ON DELETE SET NULL (created_by)`。用户被物理删除时只置空作者，不删业务数据。
- **结果**：`violates foreign key constraint "fk_tag_creator"` ✅
- **刻意不补**：`task_log.operator_id`、`sys_audit_log.user_id`、`extract_result_history.operator_id` —— 审计表要能在账号消失后继续留证，加外键反而制造删除死锁。

#### P1-4 IMAP UIDVALIDITY 缺失会导致「静默漏收附件」

- **问题**：IMAP 的 `UID` 只在同一 `UIDVALIDITY` 内单调。邮箱重建、服务器迁移、文件夹重建后 `UIDVALIDITY` 变化，`UID` 会从头重用。而幂等键是 `(config_id, message_uid, attachment_index)`——新邮件会撞上旧 UID，被当成「已入队」直接丢弃。
- **特点**：**不报错、不告警**，表现为「附件少了」，是最难排查的一类问题。
- **修复**：`dept_email_config.uid_validity` + `email_inbox.uid_validity`（默认 0），唯一键扩为 `(config_id, uid_validity, message_uid, attachment_index)`。
- **应用层约束**：拉信前比对 `UIDVALIDITY`，与库中记录不一致时清零 `last_uid` 并全量重扫。

#### P1-5 同部门邮箱可重复

- **问题**：登录名全局唯一，但 `email` 完全没有约束。同部门建两个同邮箱账号会导致通知重复投递、找回密码错发。
- **证据**：T14 两条同邮箱账号均写入成功。
- **修复**：`uq_user_email (dept_id, lower(email)) WHERE deleted_at IS NULL AND email IS NOT NULL`。
- **结果**：`duplicate key value violates unique constraint "uq_user_email"` ✅

### P2（性能 / 可运维，不影响正确性）

#### P2-1 跨租户向量召回率下降

- **问题**：HNSW 是全局单索引，默认策略是「先取 topN 再按 `dept_id` 过滤」。租户多、单租户块少时，过滤后剩不下几条，召回率明显掉——多租户 RAG 的典型坑。
- **实测**：`hnsw.iterative_scan = off`。
- **修复**：`ALTER DATABASE docai SET hnsw.iterative_scan = 'relaxed_order'`，让索引在过滤后继续扫描直到凑满结果。
- **结果**：已生效（`show hnsw.iterative_scan` → `relaxed_order`）。
- **注**：这是本库唯一一处「配置层」修复，不在 DDL 内，已写进 003 便于复现。

#### P2-2 版本表缺存储桶

- **问题**：`document_version` 只有 `storage_key`，隐含「与主文件同桶」。文件跨桶迁移或换存储后端后，历史版本定位不到。
- **修复**：新增 `storage_bucket VARCHAR(64) NOT NULL DEFAULT 'docai'`。

#### P2-3 复合唯一索引的写放大（已知代价，接受）

- 新增 `uq_block_id_dept` 使 `document_block` 多一个唯一索引。在千万行量级上，每次写入多维护一棵索引。
- **这是复合外键的必要代价**（被引用表必须有 `UNIQUE(id, dept_id)`）。当前表为空，无实际影响。
- **触发回退的条件**：`document_block` 超过 3000 万行且写入成为瓶颈时，可把 `fk_res_block` 换回单列外键 + 同租户校验触发器，省掉这个索引。已写入演进清单。

---

## 四、修复后实测对照

| 用例 | 检修前 | 检修后 | 拦截者 |
|---|---|---|---|
| T1 版本跨租户 | 写入成功 ❌ | 拒绝 | `fk_ver_doc` |
| T2 邮件配置跨租户 | 写入成功 ❌ | 拒绝 | `fk_inbox_config` |
| T3 证据块跨租户 | 写入成功 ❌ | 拒绝 | `fk_res_block` |
| T4 结果挂非任务文件 | 写入成功 ❌ | 拒绝 | `trg_res_doc_in_task` |
| T5 完成但无完成时间 | 写入成功 ❌ | 拒绝 | `ck_task_terminal_time` |
| T6 失败但无原因 | 写入成功 ❌ | 拒绝 | `ck_task_failed_msg` |
| T7 驳回但无理由 | 写入成功 ❌ | 拒绝 | `ck_inbox_rejected_reason` |
| T8 认领但未入库 | 写入成功 ❌ | 拒绝 | `ck_inbox_claimed_doc` |
| T9 解析完成但无时间 | 写入成功 ❌ | 拒绝 | `ck_doc_parsed_at` |
| T10 表格块无结构化内容 | 写入成功 ❌ | 拒绝 | `ck_block_table_json` |
| T11 作者指向不存在用户 | 写入成功 ❌ | 拒绝 | `fk_tag_creator` |
| T12a 篡改审计日志 | 更新成功 ❌ | 拒绝 | `trg_no_update_task_log` |
| T12b 删除审计日志 | 删除成功 ❌ | 拒绝 | `trg_no_update_task_log` |
| T13 部门树成环 | 更新成功 ❌ | 拒绝 | `trg_dept_no_cycle` |
| T14 同部门同邮箱 | 写入成功 ❌ | 拒绝 | `uq_user_email` |
| T15 已完成但进度 0 | 写入成功 ❌ | 拒绝 | `ck_task_done_progress` |

**回归验证**：模拟完整业务链路（上传 `USER/UPLOADED` → 写 `PARAGRAPH` 块 → 建任务 → 挂源文件 → 写抽取结果 → 置 `COMPLETED`）全部通过，新约束未误伤正常路径。

---

## 五、应用层必须遵守的新约束

这些约束在数据库层已强制，代码需配合，否则会在运行期报错：

1. **先挂文件，再写结果**：写 `extract_result` 之前必须先写 `task_document_rel`，否则触发 `trg_res_doc_in_task`。
2. **状态跃迁要带齐字段**：置 `COMPLETED` 必须同时给 `progress=100` 和 `completed_at`；置 `FAILED` 必须给 `error_msg`。
3. **解析状态要带齐字段**：置 `PARSED` 必须同时给 `parsed_at`；置 `FAILED` 必须给 `parse_error`。
4. **邮件认领要带 `document_id`**；驳回要带 `reject_reason`。
5. **表格块必须落 `table_json`**，不能只存纯文本。
6. **审计表只能 INSERT**，任何 UPDATE/DELETE/TRUNCATE 都会被拒——包括调试期的临时清理。
7. **IMAP 拉取必须读写 `uid_validity`**，不能只看 `last_uid`。
8. **部门 `parent_id` 不能成环**，上挂前先确认目标不在自己的子树内。

> ⚠️ 第 1 条与阶段 4（抽取链路重写）直接相关：现有 `ExtractionPipelineService` 尚无 `task_document_rel` 写入，迁移时必须先建关系再落结果。

---

## 六、未纳入本次的问题（附理由）

| 事项 | 判断 | 理由 / 触发条件 |
|---|---|---|
| `uuid-ossp` 扩展未使用 | 保留 | 主键全为 IDENTITY，无 uuid 列。删除收益低、需确认无外部依赖，风险大于收益 |
| `extract_result_history` 无 `dept_id` | 保留 | 通过 `result_id` 已能定位租户，加冗余列只为省一次 join，不符合当前规模 |
| 审计表 `user_id` / `operator_id` 无外键 | 保留 | 有意为之：账号注销后审计仍需可读 |
| `document_version.version_no` 并发竞态 | **待应用层解决** | `SELECT MAX+1` 在并发下会撞 `uq_ver_doc_no`。schema 无法根治，需在保存版本时加行锁或 advisory lock |
| `document_block` 分区 | 演进 | 触发条件：>3000 万行或表+索引 > 内存 1/4 |
| `task.progress` 高频写 | 按设计 | 已约定进度写 Redis，仅 0/50/100 与状态跃迁落库 |
| `embedding_dim` 与 `vector(1536)` 一致性 | 保留 | 维度已硬编码在列类型上，换模型必须改表，冗余列仅作标识 |
| 设计文档 CHECK 计数写 29、实际 27 | **本报告更正** | 原文档笔误；检修后实际 36 条，见第二节 |

另：`pg_trgm` 对中文短词的切分已实测——`金额`、`甲方` 均能切出 3 个 trigram，`LIKE '%金额%'` 可走 GIN 索引，**不存在「中文 2 字词走不了索引」的问题**，无需额外处理。

---

## 七、回滚

```bash
# 方式一：只回滚本次检修（删掉 003 新增对象，保留数据）
#   见 003 脚本，所有对象均为 DROP IF EXISTS + 重建，逐条反向执行即可

# 方式二：整库回到检修前（会丢检修后的写入）
docker exec docai-postgres psql -U docai -d docai -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker exec -i docai-postgres psql -U docai -d docai -v ON_ERROR_STOP=1 \
    < data/backup/postgres-v3-pre-hardening-20261103.sql
```

**纪律**：`002_schema_v3.sql` 已执行，此后冻结；本次变更全部在 `003_db_hardening.sql` 中，可整份重放（幂等）。

---

## 八、附录：数据库层过程化对象已移除（2026-11-03 16:50）

按「数据库只负责表结构，业务规则放 Service 层」的项目约定，执行 `sql/init/004_drop_triggers_functions.sql` 删除了第三节中引入的全部 **18 个触发器**与 **5 个 PL/pgSQL 函数**。库中现只剩表、约束、索引。

被移除的规则及其新归属：

| 原实现（库内） | 现在由谁负责 |
|---|---|
| `updated_at` 自动维护（11 张表） | 应用层写入 |
| `parent_id = 0` → `NULL` 归一 | `DeptService` / `DataInitializer`（已改为直接写 `NULL`） |
| 部门树防环 | Service 层校验 |
| `task_log` / `sys_audit_log` 防改写 | 应用层只写不改（如需强约束，改用数据库账号权限） |
| 抽取结果必须属于任务源文件 | Service 层校验 |

**保留的声明式约束**（属于建表语句本身）：36 个外键、36 个 CHECK、7 个唯一约束、68 个索引。

**已实测影响**：`parent_id = 0` 直插会撞 `sys_dept_parent_id_fkey`，因此同步修改了两处 Java 代码——
`DataInitializer` 写 `null`、`DeptService` 把 `0` 归一为 `null`（对外仍接受 0 表示根部门）。`mvnw compile` 通过。

**待办**：`updated_at` 现无人维护。若使用 MyBatis-Plus，建议加 `MetaObjectHandler` 做 `INSERT_UPDATE` 自动填充；否则需在各 Service 的 update 中显式赋值。

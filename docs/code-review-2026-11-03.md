# 全项目代码审查报告

> 审查时间：2026-11-03
> 范围：后端 `backend/`（约 7600 行 Java）+ 前端 `frontend/` + 配置
> 审查重点：代码质量、逻辑正确性、潜在缺陷、边界情况、一致性

---

## 一、总体结论

项目已跑通「认证/租户隔离 + 资产入库 + 文档操作 + 填表 + 抽取」，核心链路清晰、分层合理。但存在**一条贯穿性的严重问题**：v3 数据库已重构为 18 表，而「任务、抽取字段、抽取结果、文档块」这条链路的后端代码**仍停留在 v1 的旧表结构**，运行到这些接口会直接 SQL 报错。

按严重程度分级如下（P0 = 运行即错/安全，P1 = 逻辑缺陷，P2 = 健壮性/风格）。

---

## 二、问题清单

### P0-1 任务链路与 v3 表结构脱节（运行即错）

**位置**：`entity/Task.java`、`service/TaskService.java`、`controller/TaskController.java`、`service/AsyncJobService.java`、`service/IngestService.java`

**成因**：`Task` 实体映射 v1 旧列 `task_id / payload / result / finished_at`，而 v3 `task` 表已改为 `payload_json / result_json / completed_at`，且**没有 `task_id` 列**（用自增 `id`）。同时 v3 的 `status` 枚举是 `PENDING/ASSIGNED/PROCESSING/COMPLETED/FAILED/CANCELLED`，而 `TaskService` 写入的是 `RUNNING / SUCCESS`。

**影响**：
- `submit()` 里 `task.setTaskId(UUID)` → 插入 `task_id` 列不存在，直接 `SQLSyntaxError`；
- `executeAsync/runDemoTask` 写 `RUNNING/SUCCESS` → 违反 `ck_task_status` CHECK；
- 所有依赖任务中心的下游（ingest、extract、fill 异步）全部连带失败。

### P0-2 提取链路与 v3 表结构脱节（运行即错）

**位置**：`entity/DocChunk.java`（映射 `doc_chunk`）、`entity/ExtractField.java`（映射 `extract_field`）、`entity/ExtractResult.java`、`dao/ChunkVectorDao.java`、`service/RetrievalService.java`、`service/IndexingService.java`、`service/ExtractionPipelineService.java`

**成因**：v3 已把 `doc_chunk` 改名为 `document_block`、把 `extract_field` 并入 `extract_schema.schema_json`、把 `extract_result` 的列改为 `field_key / field_value / evidence_block_id / is_verified`。而：
- `DocChunk` 仍 `@TableName("doc_chunk")`，且列名 `seq / chunk_type` 与 v3 的 `block_index / block_type` 不符；
- `ChunkVectorDao` 三条裸 SQL 硬编码 `doc_chunk`；
- `ExtractResult` 用 `field_id / field_name / value / chunk_id / evidence / verified`，均与 v3 不符，且缺 `task_id / dept_id` 两个 NOT NULL 列。

**影响**：`/api/fields`、`/api/extract/*`、跨文档检索、`/api/ingest` 的索引入库全部报错。

### P0-3 登录接口缺少密码兜底（逻辑缺陷）

**位置**：`service/AuthService.login`

**成因**：`passwordEncoder.matches(request.password(), ...)` 在 `request.password()` 为 `null` 时会 NPE（`LoginRequest` 无校验，`password` 可能为空）。

**影响**：空密码请求导致 500，而非 401。

### P0-4 部门层级无校验（数据一致性问题）

**位置**：`service/DeptService.create`、`rename`

**成因**：`create` 接受任意 `parentId`，不校验父部门是否存在、是否属于同租户、是否成环；`rename` 不查重（create 查了重名，rename 不查）。

**影响**：可挂到不存在的父部门（外键会拦，但报错不友好）、可改出重名部门（数据库 `uq_dept_parent_name` 会拦，同样报错不友好）。此外 `create` 的重名检查用的是「全局按 name」，与数据库「同父级下不重名」语义不一致。

### P0-5 抽取结果缺 task_id 上下文（逻辑缺陷）

**位置**：`service/ExtractionPipelineService.extractAndSave`

**成因**：`ExtractResult` 写入时只填 `documentId`，不填 `task_id`（v3 `extract_result.task_id` 是 NOT NULL）。即使修正实体映射，无 task 上下文也无法落库。

**影响**：抽取结果无法归到任务，v3 的「结果必须挂在任务源文件清单」语义无法满足。

---

### P1-1 部门删除的层级约束缺失

**位置**：`DeptService.delete`

**成因**：只检查「有用户则拒删」，不检查「有子部门则拒删」。删除有子部门的父部门会导致子部门 `parent_id` 指向已软删部门，树结构断裂。

### P1-2 预签名 URL 的过期参数未生效

**位置**：`StorageService.presignedUrl`、`AssetService.presignedUrl`

**成因**：`AssetService.presignedUrl(id, expireSeconds)` 接收了 `expireSeconds` 但 `StorageService.presignedUrl(key)` 写死 1 小时，参数被丢弃。

### P1-3 用户邮箱/手机号未校验格式

**位置**：`SysUserService.create/update`

**成因**：`email`、`phone` 直接写入，无格式校验；v3 有 `uq_user_email (dept_id, lower(email))` 唯一约束，非法或重复邮箱会触发底层异常而非友好提示。

### P1-4 角色越权管理边界不完整

**位置**：`SysUserService`

**成因**：`create` 里 ADMIN 之外的分配员也能建用户，但 `require()` 只对「非 ADMIN 且跨部门」做拦截；分配员建用户时 `deptId` 缺省取 `CurrentUser.deptId()`，但**显式传 `deptId` 时未校验是否等于本部门**，分配员可建跨部门用户。

### P1-5 任务进度回写与并发

**位置**：`TaskService`（v3 重构后）

**成因**：`updateById` 无乐观锁保护，`version_no` 字段未使用；多线程同时更新任务（ingest 每文件更新一次进度）可能互相覆盖。

### P2-1 `me()` 与登录状态一致性

**位置**：`AuthService.me`

**成因**：`me()` 查库返回最新状态，但 JWT 中的角色在签发后 720 分钟内不变，`@PreAuthorize` 鉴权用的是令牌中的角色，存在「禁用后仍可用旧令牌」的窗口（代码注释已说明，属已知限制）。

### P2-2 异常处理细节

**位置**：`GlobalExceptionHandler`、`AssetController.batch`

**成因**：
- `AssetController.batch` 用 `throw new BizException` 而非 `Result.fail`，但两者最终都走统一处理器，语义一致但风格不统一；
- `handleOther` 兜底返回 `code=500`，HTTP 仍 200，前端需同时判断 HTTP 与 code。

### P2-3 硬编码与魔法值

**位置**：多处 `"RUNNING"/"SUCCESS"` 状态字符串、`"PENDING"` 等散落，无常量收敛；`TaskController` 的 `Map.of` 返回结构不一致。

### P2-4 前端单文件过大

**位置**：`frontend/src/App.tsx`（380 行）

**成因**：单页控制台所有逻辑集中一个文件，无组件拆分，可维护性差（不影响功能）。

---

## 三、修复计划

| 优先级 | 问题 | 修复方式 | 风险 |
|---|---|---|---|
| P0-1 | Task 对齐 v3 | 重写 `Task` 实体为 v3 列 + 六态状态机，`TaskService` 改用 `id` 作任务号、`PROCESSING/COMPLETED` | 中（涉及多处调用） |
| P0-2 | 提取链路对齐 v3 | 重写 `DocChunk→DocumentBlock`、`ExtractField` 暂用 schema、`ExtractResult` 列名对齐、DAO 改表名 | 高（涉及面广） |
| P0-3 | 登录空密码 | `login` 增加 null 判断 | 低 |
| P0-4 | 部门校验 | create 校验父部门存在 + rename 查重 | 低 |
| P0-5 | 结果 task 上下文 | 抽取结果强制要求 task 上下文 | 中 |
| P1-* | 部门删除层级、预签名参数、邮箱校验、越权 | 逐项小改 | 低 |
| P2-* | 状态常量、风格 | 收敛常量 | 低 |

---

## 四、修复进度

### 已完成（2026-11-03）

| 编号 | 修复内容 | 涉及文件 |
|---|---|---|
| P0-1 | `Task` 实体重写为 v3 列（id 自增作任务号，六态状态机 + 常量），`TaskService` 改用 Long 任务号 + PROCESSING/COMPLETED/FAILED，移除已废弃的 demo 任务；`AsyncJobService`/`IngestService`/`TaskController`/`AsyncJobController`/`IngestController` 同步 | Task.java, TaskService.java, AsyncJobService.java, IngestService.java, TaskController.java, AsyncJobController.java, IngestController.java |
| P0-2(部分) | `DocChunk` 映射 `document_block`（block_index/block_type/dept_id），`ChunkVectorDao` 改表名 + 租户过滤，`RetrievalService` 加 dept_id 过滤，`IndexingService` 写 v3 列，`ExtractResult` 重写为 v3 列，`ExtractionPipelineService` 同步 | DocChunk.java, ChunkVectorDao.java, RetrievalService.java, IndexingService.java, ExtractResult.java, ExtractionPipelineService.java |
| P0-3 | 登录空密码 NPE 修复 | AuthService.java |
| P0-4 | 部门 create 校验父部门存在 + 同级重名；rename 查重 | DeptService.java |
| P1-1 | 部门删除增加子部门检查 | DeptService.java |
| P1-2 | 预签名 URL 过期参数生效（1~7 天钳制） | AssetService.java, StorageService.java |
| P1-4 | 分配员建用户强制本部门 | SysUserService.java |

验证：`mvnw compile` 通过，68 个单元测试全绿。

### 待决策（结构性，需用户确认方向）

> **已确认并完成（用户选择「对齐 v3」+「先建任务再写结果」）**

**① `ExtractField` → `extract_schema` 迁移（方案 A）**
- `ExtractField` 改为纯字段模型（不再映射表），新增 `ExtractSchema` 实体 + `ExtractSchemaMapper` + `ExtractSchemaService`（字段存 `schema_json`，支持自然语言描述）。
- 删除 `ExtractFieldMapper`、`ExtractFieldService`、`ExtractFieldController`，新建 `SchemaController`（`/api/schemas`）。
- `ExtractionPipelineService` 从 schema 读字段。

**② `extract_result.task_id` 归属（先建任务再写结果）**
- 新增 `TaskDocumentRel` 实体 + `TaskDocumentRelMapper`。
- `AsyncJobService.runExtract` 与 `ExtractionResultController.persist` 均先建 task → 挂 `task_document_rel` → 写 `extract_result`（带 `task_id`）。

### 最终验证

`mvnw compile` / `mvnw test`（68 测试全绿）/ `mvnw package`（jar 96MB）全部通过；旧表名与旧方法残留已扫描确认清除。

### 遗留待优化点（未改，供后续关注）

1. **`updated_at` 无人维护**：触发器已移除，各 Service 的 update 未显式赋值（MyBatis-Plus 建议加 `MetaObjectHandler`）。
2. **`version_no` 乐观锁未使用**：`TaskService.updateById` 未做 `WHERE version_no = ?` 条件更新，并发进度回写仍可能互相覆盖。
3. **前端 `App.tsx` 单文件 380 行**：可组件化拆分，不影响功能。
4. **`me()` 与 JWT 角色时效**：令牌签发后 720 分钟角色不变，禁用用户需等令牌过期（已在注释说明）。
5. **`ExtractSchema` 的 `promptText → schema_json` 生成逻辑**：目前由调用方直接传字段，尚未实现「自然语言自动生成字段定义」的 LLM 环节（赛题要求，需后续接入）。




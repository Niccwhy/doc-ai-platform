# 架构 v2：多租户文档处理流水线

> **数据库已升级到 v3（18 表，2026-11-03）**：本文第三节的 12 表结构为历史记录，
> 当前结构见 `docs/db-design-v3.md` 与 `sql/init/002_schema_v3.sql`。
> v3 保留了 partner 的全部表名、列名与建模关系，只补约束/并发/隔离/审计层。

> 设计基准：partner 提供的《修改.md》与《数据库表.md》。本文件记录落地时的设计决策、
> 与其原始设计的差异、以及分阶段实施进度。
> 更新于 2026-11-03。

## 一、总体定位

以「部门（租户）」为数据隔离边界的企业级文档处理流水线：

```
多源入库（上传/邮件） → 标签管理 → 任务派发 → AI 抽取/填表 → 人工复核 → 产出归档
```

四个角色：外部实体（邮件来源）、系统管理员（ADMIN）、分配员（DISPATCHER）、处理员（PROCESSOR）。

## 二、相对 partner 原始设计的差异

保持其表名、列名与整体结构不变，仅做必要补充（每处 DDL 内均以「补充：」注释标注）：

| 表 | 补充项 | 原因 |
|---|---|---|
| `document` | `error_msg` | 解析失败需留痕，否则只能看日志 |
| `document_block` | `heading_path`、`cell_ref` | 填表要按标题层级/单元格坐标定位，原设计只有 content |
| `document`（时间/状态） | 统一 `TIMESTAMPTZ` | 原设计用 `TIMESTAMP`，跨时区会偏 8 小时 |
| `sys_user` | `status` 改 `VARCHAR`、加 `email` | 与 `task.status` 风格一致；邮箱用于通知 |
| `dept_email_config` | `last_uid`、`last_check_at`、`folder`、`use_ssl`、`enabled` | **IMAP 增量拉取必需**，否则每次全量重复入库 |
| `task` | `progress`、`payload`、`result_json`、`error_msg`、`started_at` | **异步任务与 SSE 进度必需**，原设计只有状态机 |
| `extract_result` | `raw_value`、`evidence_text`、`verified`、`modified_by`、`updated_at` | 复核需要区分"模型原值/归一化值"与"改过/已确认" |
| `extract_schema` | `updated_at`、`deleted_at` | 人工微调与软删除 |
| 全表 | 外键约束 24 个、必要索引 | 原设计只有索引无外键，容易产生脏引用 |

未采用的设计建议：`Tika` 解析。赛题样本仅 docx/md/xlsx/txt，POI + flexmark 已覆盖；
Tika 会引入数十 MB 依赖并拖慢启动，PDF 支持留作扩展点。

## 三、数据模型

12 张表，按模块分组：

| 模块 | 表 | 说明 |
|---|---|---|
| 组织 | `sys_dept`、`sys_user`、`dept_email_config` | 租户、账号、部门邮箱 |
| 资产 | `document`、`sys_tag`、`document_tag_rel` | 统一文件元数据（源文件/模板/产出）、标签多对多 |
| 解析 | `document_block` | 段落/表格块 + `vector(1536)` + HNSW 索引 |
| 抽取 | `extract_schema` | 自然语言描述生成的字段 Schema（JSONB） |
| 任务 | `task`、`task_document_rel` | 任务状态机 + 与文件的多对多 |
| 结果 | `extract_result`、`task_log` | 字段级结果（含证据）与操作审计 |

关键约束：
- `document` 上 `UNIQUE(dept_id, sha256)`：同部门去重，重复上传直接复用已有文件。
- `document_block.embedding` 为 `vector(1536)`，HNSW 余弦索引 `idx_block_embedding`。
- 所有业务表带 `dept_id`，服务层强制按当前用户部门过滤。

## 四、认证与权限模型

- **登录**：`POST /api/auth/login` → JWT（HS384，默认 12 小时），令牌内含 `userId / deptId / role`。
- **鉴权**：`SecurityFilterChain` 无状态校验 + `@PreAuthorize` 方法级控制。
- **错误约定**：认证失败 HTTP 401、权限不足 HTTP 403，均返回统一 `{code,message,data}`；
  业务错误保持 HTTP 200 + `code`，便于前端区分"要重新登录"与"业务提示"。
- **租户隔离**：服务层通过 `CurrentUser.deptId()` 强制过滤；非管理员查询一律限定本部门。

| 能力 | ADMIN | DISPATCHER | PROCESSOR |
|---|---|---|---|
| 部门增删改 | ✓ | — | — |
| 用户增删改 | ✓ | — | — |
| 查看用户列表 | 全部部门 | 本部门 | — |

## 五、实施进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 12 张表 DDL、legacy 归档、索引与约束 | 已完成并验证 |
| 1 | 部门/用户/登录/JWT/三角色鉴权/租户隔离 | 已完成并验证 |
| 2 | 资产池：上传去重、分类、标签、IMAP 邮件接入 | 待开始 |
| 3 | 任务：Schema 生成、建单、派发、状态机 | 待开始 |
| 4 | AI 链路：解析入库 block、RAG 抽取、填表导出 | 待迁移 |
| 5 | 复核产出：双屏对照、修正留痕、归档、审计 | 待开始 |
| 6 | 前端多页面（登录/文件池/任务/复核/管理）+ 联调 | 待开始 |

**当前注意**：阶段 1 完成后，原有业务接口（`/api/parse`、`/api/fill`、`/api/doc/*` 等）
仍指向 v1 表结构，**在新库上不可用**，将在阶段 4 迁移到 `document_block` 等新表。

## 六、已完成接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 登录，返回 token 与用户信息 |
| GET | `/api/auth/me` | 登录 | 当前用户 |
| GET | `/api/health` | 公开 | 健康检查 |
| GET | `/api/depts` | 登录 | 部门列表（非管理员仅本部门） |
| POST/PUT/DELETE | `/api/depts/**` | ADMIN | 部门增删改 |
| GET | `/api/users` | ADMIN/DISPATCHER | 用户列表（管理员可跨部门） |
| POST/PUT/DELETE | `/api/users/**` | ADMIN | 用户增改删、重置密码 |

## 七、默认账号（首次启动自动创建，幂等）

| 账号 | 密码 | 角色 | 部门 |
|---|---|---|---|
| admin | admin123 | ADMIN | 总部 |
| dispatcher | 123456 | DISPATCHER | 总部 |
| processor | 123456 | PROCESSOR | 总部 |

密码可用 `INIT_ADMIN_PASSWORD` 等环境变量覆盖；**上线前务必修改默认密码**。

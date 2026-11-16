# 项目开发 Roadmap

> 说明：本Roadmap仅为开发参考，实际开发中可能会根据需求变更进行调整。  
> `##整体流程`部分不可编辑，保持不变；具体落实步骤可写在 `##TODO`中。

**全局架构约束**：多部门隔离（带 `dept_id`）、逻辑删除（仅 `deleted_at`）、物理文件进 MinIO，活文档 JSON 进 PostgreSQL。

## 整体流程

### 阶段一：基座搭建与资产入库

- [ ] **1.1 基础环境与配置**
    - [x] 编写 `docker-compose.yml` 启动 PG 与 MinIO。（人类）
    - [ ] 执行建表 SQL，初始化基础部门与用户数据。（人类）
- [ ] **1.2 鉴权与多租户**
    - [ ] 配置 Spring Security + JWT 登录认证。
    - [ ] 实现请求拦截器，将用户上下文（`userId`, `deptId`）注入当前线程。（AI 生成，人类审查）
- [ ] **1.3 手动上传与去重**
    - [ ] 实现文件上传接口，计算 SHA256 并在同部门内校验去重。
    - [ ] 对接 MinIO SDK，将文件流写入对应目录（如 `dept/1/source/`）。（AI 生成，人类审查事务）
- [ ] **1.4 邮件拉取与暂存**
    - [ ] 配置 IMAP 定时任务，拉取部门邮箱附件。
    - [ ] 将附件暂存至 MinIO 的 `temp/` 前缀目录，记录写入 `email_pending` 表。（AI 生成，人类提供测试邮箱）
- [ ] **1.5 文件版本控制**
    - [ ] 解析分配员的操作，对比新旧文件，向 `document_version` 追加 V2 记录。确保旧版本文件及其解析块不受影响。（人类主导，AI 辅助）

### 阶段二：任务流转与 AI 抽取

- [ ] **2.1 任务状态机与分发**
    - [ ] 实现任务创建接口（关联文件范围、Schema、模板）。
    - [ ] 实现任务状态流转（`PENDING` -> `PROCESSING` -> `COMPLETED/FAILED`）及自动分配逻辑。（AI 生成，人类审查状态约束）
- [ ] **2.2 文档解析与向量化**
    - [ ] 封装 Apache Tika 接口，将入库文件解析为 `document_block` 结构（区分段落/表格）。
    - [ ] 调用 Embedding 模型，将文本块向量化并写入 pgvector。（AI 生成，人类审查切块策略）
- [ ] **2.3 异步执行与实时推送**
    - [ ] 配置 `@Async` 线程池，异步执行耗时的解析与 AI 处理。
    - [ ] 使用 SSE (`SseEmitter`) 将后台任务的执行进度实时推送给前端。（AI 生成，人类排查连接泄漏）
- [ ] **2.4 EXTRACT 抽取任务**
    - [ ] 结合 pgvector 检索与 LLM API，按 Schema 提取字段。
    - [ ] 解析大模型返回结果，写入 `extract_result` 表，并确保带有置信度与证据块 ID。（人类主导 Prompt 设计，AI 写调用代码）
- [ ] **2.5 FILL 填表任务**
    - [ ] 读取模板占位符坐标。
    - [ ] 利用 Apache POI，将抽取数据回填至 Word/Excel 模板，生成最终物理文件上传至 MinIO。（AI 生成，人类解决复杂格式兼容）

### 阶段三：活文档协作与复核
- [ ] **3.1 JSON 树结构设计与生成**
    - [ ] 定义包含证据溯源（references）的富文本 JSON 结构。
    - [ ] 驱动 LLM 生成包含溯源标记的 EDIT_DOC 活文档。
- [ ] **3.2 前端编辑器开发**
    - [ ] 开发解析 JSON 的富文本渲染页面。
    - [ ] 实现段落级溯源标记的交互（点击高亮并展示原文档证据）。
- [ ] **3.3 活文档修改与导出**
    - [ ] 实现手动编辑或自然语言修改后的版本追加（V1 -> V2）。
    - [ ] 实现将 JSON 树渲染并导出为 Word/PDF 文件。

### 阶段四：测试与交付
- [ ] **4.1 单元测试编写**
    - [ ] 使用 JUnit 5 + Mockito 覆盖越权拦截、状态机异常流转等核心逻辑。
- [ ] **4.2 前后端联调与闭环**
    - [ ] 修复接口对接问题，跑通“分配 -> 抽取 -> 复核 -> 归档”的完整流程。
- [ ] **4.3 指标验证**
    - [ ] 使用官方样本集测试填表准确率（>80%）和处理耗时（<90s）。
- [ ] **4.4 文档撰写与答辩准备**
    - [ ] 生成需求分析、数据库设计、接口文档及最终测试报告。


## TODO

### 1.2 鉴权与多租户（已实现，待人类审查）
- 认证方式：JWT（HS256），登录接口 `POST /api/v1/auth/login`；Token Claims 包含 `uid`、`deptId`、`role`，默认有效期 120 分钟。
- 配置项：`security.jwt.secret`、`security.jwt.expiration-minutes`，均可通过环境变量 `JWT_SECRET`、`JWT_EXPIRATION_MINUTES` 覆盖。
- 上下文注入：`JwtAuthenticationFilter` 解析 Token 后写入 `UserContextHolder`（ThreadLocal）与 Spring SecurityContext，请求结束统一清理；未登录/无效 Token 返回 401 的 `Result` 结构。
- 访问控制：除 `/api/v1/auth/login` 和 `/error` 外全部接口需要认证；角色以 `ROLE_{role}` 形式写入权限集合，后续可接入 `@PreAuthorize`。
- 待后续：异步线程池上下文传递、SSE 鉴权、刷新 Token。

### 1.3 手动上传与去重（已实现，待人类审查）
- 上传接口：`POST /api/v1/documents`（multipart：`file`、`fileCategory`，默认 `SOURCE`，仅允许 `SOURCE/TEMPLATE`）。
- 去重规则：流式计算 SHA-256，在同部门内按 `document_version.sha256 + is_current=1 + document.deleted_at IS NULL` 判重；命中直接返回已有记录并标记 `duplicate=true`，不重复写 MinIO。
- 存储路径：`dept/{deptId}/source|template/{uuid}.{ext}`，桶名默认 `docai`，首次上传时自动建桶。
- 事务：文件元数据与版本记录写入在同一事务内完成。
- 待后续：并发去重唯一约束、标签关联、上传后自动解析、MinIO 写入失败时的补偿删除。

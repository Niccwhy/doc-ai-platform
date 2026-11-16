# 后端代码组织说明

## 1. 技术边界

后端位于 `backend/`，采用 Spring Boot + MyBatis-Plus，按业务领域组织代码。数据库使用 PostgreSQL/pgvector，物理文件使用 MinIO，耗时处理使用 Spring `@Async`，任务进度使用 SSE 推送。

统一约定：

- Controller 只负责 HTTP 参数接收、校验和响应转换。
- Service 负责业务规则、事务、权限校验和任务状态流转。
- Mapper 负责 MyBatis-Plus 数据访问。
- Entity 只负责数据库表映射，不承载跨模块业务流程。
- DTO 用于接口请求和响应，避免直接暴露 Entity。
- Controller 返回 `Result<T>`，文件下载和 SSE 除外。
- 所有部门资产查询和写入都必须带有 `dept_id` 隔离条件。

## 2. 顶层目录

```text
backend/src/main/java/cn/edu/zjut/backend/
├── BackendApplication.java
├── common/                 # Result、分页对象等通用模型
├── config/                 # Spring、MyBatis-Plus、异步、MinIO 等配置
├── exception/              # 业务异常和全局异常处理
├── security/               # JWT、用户上下文、部门权限
├── auth/                   # 用户、部门、角色和邮箱配置
├── document/               # 文件、文件版本、标签
├── task/                   # 任务、任务文件关联、任务日志
├── parsing/                # Tika/POI 解析和文档块
├── rag/                    # Embedding 和 pgvector 检索
├── extract/                # Schema、LLM 抽取和抽取结果
├── fill/                   # Word/Excel 模板填充
├── editable/               # JSON 活文档和版本
├── storage/                # MinIO 文件存储
├── email/                  # IMAP 邮件和附件暂存
└── audit/                  # 操作审计和 AI 调用日志
```

当前已实现 `common/`、`config/`、`exception/`，并已按业务领域创建部分 Entity。`controller/`、`service/`、`mapper/`、`dto/` 在各业务模块中随着功能实现逐步增加，不预先创建空占位文件。

## 3. 公共基础层

### `common/`

- `Result<T>`：统一接口响应，包含 `code`、`message`、`data` 和 `traceId`。
- `PageResult<T>`：统一分页响应，包含 `total`、`pageNo`、`pageSize` 和 `list`。

### `config/`

- `AsyncConfig`：异步线程池和异常处理。
- `MybatisPlusConfig`：Mapper 扫描和 PostgreSQL 分页插件。
- `MinioConfig`、`MinioProperties`：MinIO 客户端及配置绑定。

后续需要在此处补充异步上下文传递、线程池配置外置、JSONB/pgvector TypeHandler 和逻辑删除配置。

### `exception/`

- `BizException`：业务失败异常。
- `GlobalExceptionHandler`：统一处理业务异常、参数校验异常、数据库约束异常和未预期异常。

## 4. 业务模块组织

每个主要业务模块使用相同的内部结构：

```text
module/
├── controller/       # REST/SSE 接口
├── service/          # 业务逻辑和事务
├── mapper/           # MyBatis-Plus Mapper
├── entity/           # 数据库表实体
└── dto/              # 请求和响应对象
```

### `auth/`

负责 `sys_dept`、`sys_user`、`dept_email_config`，以及登录、用户角色和部门权限。JWT 过滤器和当前用户上下文放在 `security/`，不放入 Controller。

### `document/`

负责 `document`、`document_version`、`sys_tag` 和标签关联。上传、SHA-256 去重、版本追加和文件元数据管理属于此模块；MinIO 读写通过 `storage/` 提供的服务完成。

### `task/`

负责 `task`、`task_document_rel`、`task_log`。任务创建、分配、状态机和异步执行入口属于此模块。状态只能通过 Service 变更，不能由 Controller 直接修改。

### `parsing/`

负责使用 Apache Tika/POI 读取文件，生成 `document_block`。解析结果应保留文档版本关联，不能覆盖旧版本的解析块。

### `rag/`

负责文本块向量化、Embedding 调用和 pgvector 相似度检索。向量类型处理、相似度 SQL 和切块策略集中在此模块。

### `extract/`

负责抽取 Schema、Prompt、LLM 调用和 `extract_result`。每个字段结果必须保留置信度和证据块 ID。

### `fill/`

负责读取 Word/Excel 模板、填充抽取结果并生成导出文件。生成的物理文件通过 `storage/` 保存，并写入 `document` 和 `document_version`。

### `editable/`

负责 `editable_doc`、`editable_doc_version`，保存 JSON 活文档、版本追加、AI 修改和人工修改。活文档 JSON 不直接写入 MinIO。

### `storage/`

负责 MinIO 对象的上传、下载、删除和部门路径隔离。建议统一生成类似 `dept/{deptId}/source/{objectKey}` 的存储路径。

### `email/`

负责 IMAP 定时拉取、邮件附件解析和 `email_pending` 暂存。附件确认后再进入正式文件资产库。

### `audit/`

负责操作日志、AI 调用日志和审计查询。任务状态变化应同步记录 `task_log`。

## 5. 依赖调用方向

推荐调用方向：

```text
Controller
    -> Service
        -> Mapper
        -> StorageService
        -> ParsingService
        -> RagService
        -> LlmService

TaskService
    -> 根据 taskType 调用 ExtractService / FillService / EditableService
```

约束：

- Controller 不直接调用 Mapper。
- Entity 不调用 Service 或其他基础设施。
- 业务模块之间通过 Service 接口协作，不直接访问对方 Mapper。
- `storage/` 不负责文档业务，只负责对象存储。
- `parsing/` 不负责任务状态流转，只返回解析结果并由任务模块更新状态。

## 6. 数据映射注意事项

目前 JSONB 字段和 `embedding` 字段在 Entity 中暂时使用 `String`，用于完成基础结构和普通读写模型。正式实现时需要：

- 为 JSONB 统一配置 JSON TypeHandler。
- 为 pgvector 配置向量类型和相似度查询方式。
- 为 `deleted_at` 配置逻辑删除策略。
- 为数据库字符串枚举建立 Java 枚举和统一转换规则。
- 为联合主键表 `document_tag_rel`、`task_document_rel` 编写明确的 Mapper 查询，避免依赖单主键 CRUD。

相关后续任务统一记录在 `docs/roadmap.md` 的 `## TODO` 中。

## 7. 推荐实现顺序

1. 完成 `security/` 和部门隔离上下文。
2. 完成 `document/`、`storage/` 文件入库闭环。
3. 完成 `task/` 任务状态机和分配。
4. 完成 `parsing/` 文档块生成。
5. 完成 `extract/` 最小抽取链路。
6. 再实现 `rag/`、`fill/` 和 `editable/` 的高级能力。
7. 最后补充 SSE、审计、统计和完整测试。

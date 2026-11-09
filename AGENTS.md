# AGENTS.md - 项目开发规范与约束

## 1. 项目概述
本系统是一个基于大模型的智能文档处理流水线，包含多源文件接入（手动/邮件）、RAG 信息抽取、模板填表、以及类似飞书的 JSON 活文档编辑与证据溯源功能。

## 2. 核心技术栈
*   **后端**: Java, Spring Boot, MyBatis-Plus
*   **前端**: React, TypeScript (AI 辅助时需遵循严格类型定义)
*   **数据库**: PostgreSQL 16 (含 pgvector 扩展)
*   **对象存储**: MinIO
*   **文档处理**: Apache Tika (解析), Apache POI (回填)
*   **异步与推送**: Spring `@Async`, SSE (Server-Sent Events)

## 3. 项目目录与访问
项目目录结构如下：
```
doc-ai-platform/
├── backend/                 # 后端服务
├── frontend/                # 前端服务
├── docs/                    # 项目文档
├── sql/                     # 数据库脚本
├── .gitignore
├── README.md
├── AGENTS.md
```
docs 目录下包含 `roadmap.md`、`db_change_log.md`、`功能模块设计.md`、`技术选型.md` 等文档，用于记录项目的功能设计、技术选型、数据库变更历史以及开发规范。

## 4. 项目约束与规范
### 4.1 代码规范
1. 代码风格：严格遵循阿里巴巴 Java 开发手册，使用 Lombok 简化代码。
2. 统一响应体：所有 Controller 返回 `Result<T>` 结构。
3. 异常处理：业务失败抛出 `BizException`，交由 `GlobalExceptionHandler` 统一拦截，禁止在 Controller 中写 try-catch 处理业务异常。
4. 事务控制：涉及多表操作（如创建任务同时关联文件）必须在 Service 层加 `@Transactional` 注解。

### 4.2 数据库操作
1. 开发中遇到需要修改数据库表结构的情况，先记录在 `docs/roadmap.md`，当修改的需求积攒到一定量再进行修改，避免频繁修改数据库表结构。
2. 所有数据库表结构、数据插入、数据更新的 SQL 脚本必须放在 `sql/` 目录下，并在 `docs/db_change_log.md` 中记录修改历史。
3. 在应用中使用 MyBatis-Plus 进行数据库操作，禁止直接使用 JDBC 或其他 ORM 框架。
4. `sql/init/01_init_schema.sql` 为初始化脚本（已经运行），包含基础部门、用户、角色、权限等数据，禁止随意修改。
5. Minio 对象存储中，所有文件必须按部门隔离存储，禁止跨部门访问（已建桶`docai`）。

### 4.3 测试规范
*   使用 JUnit 5 + Mockito 编写单元测试。
*   **重点覆盖**：越权访问拦截（跨部门测试）、任务状态机异常流转、JSON 树解析。
*   测试类严禁连接真实数据库和网络。

## 5. 项目管理
1. 开发前需阅读已有docs文档，确保对系统功能模块、技术选型、数据库设计有清晰理解。
2. 所有新功能开发前需在 `docs/roadmap.md` 中记录需求和设计，避免重复开发。
3. 所有技术引入或架构调整需要向用户提出并说明，得到认可后方可实施并在`技术选型.md`中记录。
4. 按照`docs/roadmap.md` 中的开发计划进行迭代开发，确保每个迭代周期内完成既定功能；在完成具体任务前先说明设计方案，得到认可后再进行编码，完成后给出交付物清单。
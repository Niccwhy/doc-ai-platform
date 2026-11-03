# Java 开发环境搭建与学习方向清单（doc-ai-platform）

> 适用机器实测基线：JDK 未安装（安装包 `D:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.msi` 已就位）、
> 后端 Spring Boot 4.1.2（Java 21，包 `cn.edu.zjut.backend`）、Maven Wrapper 3.10.0 未下载发行包、
> 无 `~/.m2/settings.xml`、Docker 29 可用、Node 22 + npm 10（无 pnpm）、IntelliJ IDEA 2025.2.3 已装。
>
> 每个方向 = 目标 / 大致内容 / 完成后的成果（可验证）。按依赖顺序排列，逐项打勾即可。

---

## 总览打卡表

> 图例：☑ 已完成并验证 ｜ ◐ 代码就绪但未完成端到端验证 ｜ ☐ 未开始
> 更新于 2026-11-03，状态以代码与单元测试为准（详见文末「进度说明」）。

| # | 方向 | 难度 | 依赖 | 状态 |
|---|------|------|------|------|
| 0 | JDK 21 安装与环境变量 | ★ | — | ☑ |
| 1 | Maven 可用 + 国内镜像加速 | ★★ | 0 | ☑ |
| 2 | 跑通 Spring Boot 骨架（Hello API） | ★★ | 1 | ☑ |
| 3 | 工程分层规范与统一返回体 | ★★ | 2 | ☑ |
| 4 | 接通 PostgreSQL + pgvector | ★★★ | 3, Docker | ☑ |
| 5 | 接通 silo(MinIO) 对象存储 | ★★★ | 3, Docker | ☑ |
| 6 | 文档解析（docx/xlsx/md/txt） | ★★★ | 5 | ☑ |
| 7 | 异步任务与 API 异步化 | ★★★ | 4, 6 | ☑ |
| 8 | 向量检索 + LLM 结构化提取 | ★★★★ | 4, 7 | ◐ |
| 9 | 模板表格自动填写 | ★★★★ | 6, 8 | ◐ |
| 10 | 性能与准确率达标（≤90s / >80%） | ★★★★ | 7, 9 | ☐ |
| 11 | 前后端联调与交付 | ★★★ | 2, 10 | ◐ |

**进度说明（2026-11-03）**

- 方向 8：LLM 网关、Embedding 客户端、pgvector 写入、双路召回、抽取校验与入库均已实现；
  缺少真实 Key，端到端语义检索与抽取准确率尚未实测。
- 方向 9：表头识别、字段映射、检索填充、按坐标保真回填与导出已实现；准确率待评测脚本实测。
- 方向 11：前端页面与 Nginx 反代已就绪，`npm run build` 通过；未在浏览器做端到端联调。

---

## 方向 0：JDK 21 安装与环境变量（你正在做的这一步）

**目标**：任意终端里 `java`、`javac` 都能执行且版本为 21，`JAVA_HOME` 指向 JDK 根目录。

**大致内容**
- 双击 `D:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.msi` 安装 Temurin 21.0.13+9
- **安装路径建议选 `D:\Java\jdk-21.0.13+9`**（默认会进 `C:\Program Files\Eclipse Adoptium\...`；你习惯产出物都放 D 盘，装 D 盘后续改配置、写脚本都更省事，两者功能等价）
- 勾选安装器里的 *Set JAVA_HOME variable* / *Add to PATH* 选项可省一步；没勾就手动设：
  - `JAVA_HOME` = `D:\Java\jdk-21.0.13+9`
  - `Path` 追加 `%JAVA_HOME%\bin`（**PATH 建议用「系统属性 → 环境变量」图形界面改，不要用 `setx PATH ...`，容易把原 PATH 截断**）
- 设置完后**必须重开一个终端窗口**才会生效

**完成后的成果（可验证）**
```bash
java -version     # 期望含 "21.0.13" 与 "Temurin"
javac -version    # 期望 javac 21.0.13
echo $JAVA_HOME   # Git Bash；PowerShell 用 $env:JAVA_HOME
```
三条都对上 → 本方向完成。若报 `command not found`，先重开终端；仍不行 → PATH 没生效或装到了别的盘。

---

## 方向 1：Maven 可用 + 国内镜像加速

**目标**：`./mvnw` 能正常跑起来，依赖下载速度可接受（不走默认境外源干等）。

**大致内容**
- 你的项目用的是 **Maven Wrapper**（`backend/mvnw.cmd`），首次运行会自动下载 Maven 3.10.0 发行包（`~/.m2/wrapper/dists/apache-maven-3.10.0` 目前是空的），**不需要单独装 Maven**
- 新建 `C:\Users\LEGION\.m2\settings.xml`，配置阿里云公共镜像 + （可选）把本地仓库改到 D 盘：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <localRepository>D:/Java/m2repo</localRepository>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>aliyun public</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```
- 熟悉 Maven 生命周期：`compile` → `test` → `package`；`pom.xml` 里 `<dependency>` 怎么加

**完成后的成果（可验证）**
```bash
cd /d/code/doc-ai-platform/backend
./mvnw -v            # 期望：Apache Maven 3.10.0 + Java version: 21.0.13
./mvnw -q compile    # 期望：无报错退出，生成 target/classes
./mvnw -q package    # 期望：target/backend-0.0.1-SNAPSHOT.jar
```
`mvnw -v` 同时显示 Maven 与 Java 21 → 本方向完成。

---

## 方向 2：跑通 Spring Boot 骨架（Hello API）

**目标**：后端能本地启动，浏览器/curl 能拿到自己写的接口返回。

**大致内容**
- 看一遍 `BackendApplication.java`（`@SpringBootApplication` + `main`），理解「内嵌 Tomcat，直接 run」
- 新建 `controller/HealthController`，写 `GET /api/health` 返回 `{"status":"UP"}`
- `application.yaml` 里确认 `server.port: 8080`
- 认识 Spring Boot 4 的 starter 命名：`spring-boot-starter-webmvc`（就是老版本的 `-web`）

**完成后的成果（可验证）**
```bash
./mvnw spring-boot:run
# 另开一个终端：
curl -s http://localhost:8080/api/health     # 期望 {"status":"UP"}
```
日志出现 `Started BackendApplication in x.x seconds` 且 curl 有正确响应 → 完成。
（这一步也顺带验证了 IDEA 能直接点 Run 启动，之后都可以在 IDE 里跑。）

---

## 方向 3：工程分层规范与统一返回体

**目标**：形成一套后面所有模块都复用的代码骨架，避免每个功能各写各的。

**大致内容**
- 分层：`controller`（接请求）→ `service`（业务逻辑）→ `repository`（读写数据）→ `entity` / `dto`
- 统一返回体 `R<T>`（code / message / data）+ 全局异常处理 `@RestControllerAdvice`
- DTO 用 Java 21 的 `record` 写，省掉一半样板代码
- 日志：Slf4j + `logback`，接口出入参打点（后面排查 90s 超时全靠它）

**完成后的成果（可验证）**
- 写一个 `/api/demo/ok` 正常返回、一个 `/api/demo/err` 故意抛异常
- 前者返回 `{"code":0,"data":...}`，后者返回 `{"code":500,"message":"..."}` 且 HTTP 状态仍是 200 或按约定码
- `./mvnw test` 通过（项目已有 `BackendApplicationTests`）

---

## 方向 4：接通 PostgreSQL + pgvector（持久化层）

**目标**：Spring Boot 能连上容器里的 `docai` 库，建表、读写、并能用向量字段。

**大致内容**
- `docker compose up -d` 启动 `docai-postgres`（5432，库/用户 `docai`，密码 `docai123`）
- 在 `sql/init/` 下写初始化脚本（目录现在是空的），至少含：
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  ```
  （该目录已挂到容器的 `/docker-entrypoint-initdb.d`，**只在首次初始化时执行**，改脚本要清 `data/postgres` 重来）
- 依赖选型（三选一，建议 **MyBatis-Plus** 或 Spring Data JDBC）：
  `spring-boot-starter-jdbc` / `mybatis-plus-spring-boot4-starter` / `spring-boot-starter-data-jpa`
- `application.yaml` 配 datasource url、用户名密码、连接池（HikariCP）
- 建表：文档表、片段表（含 `vector` 字段）、任务表、抽取结果表

**完成后的成果（可验证）**
```bash
docker compose ps                    # docai-postgres 状态 healthy
./mvnw spring-boot:run               # 启动无 "Connection refused" / 无驱动类报错
curl -s localhost:8080/api/db/ping   # 自己写的探活接口，返回数据库版本
psql 里 SELECT 'vector'::regtype;    # 扩展存在
```
能插入一条带向量的记录并 `SELECT` 回来 → 完成。

---

## 方向 5：接通 silo（MinIO 兼容）对象存储

**目标**：后端能接收文件上传、存进 bucket `docai`、拿到可访问地址。

**大致内容**
- `docker compose up -d` 起 `docai-silo`（API 9000 / 控制台 9001，`minioadmin` / `minioadmin123`），bucket `docai` 你已建好
- 客户端选型：`io.minio:minio` 或 AWS SDK v2（`software.amazon.awssdk:s3`，因为 silo 兼容 S3 协议）
- 实现：`POST /api/files`（MultipartFile）→ 返回 objectKey + 预签名 URL；`GET /api/files/{key}` 下载
- 配置放 `application.yaml`，密码不要硬编码（后面用环境变量注入）

**完成后的成果（可验证）**
```bash
curl -F "file=@/d/test/sample.docx" http://localhost:8080/api/files
# 期望返回 {"key":"...","url":"http://localhost:9000/docai/..."}
```
浏览器打开 `http://localhost:9001` 能看到刚传的对象 → 完成。

---

## 方向 6：文档解析（docx / xlsx / md / txt）

**目标**：把评测样本集（5 docx + 3 md + 5 xlsx + 3 txt）解析成结构化文本与表格数据。

**大致内容**
- 依赖：`org.apache.poi:poi-ooxml`（docx/xlsx）、`commonmark`（md）、原生 IO（txt）
- 输出结构：按段落/表格切片，每片带 `docId`、`序号`、`类型(text/table)`、`原文`、`来源文件名`
- xlsx 要保留**单元格坐标与类型**（后面填表要对齐模板，这是准确率的关键）
- 大文件（docx ≥500KB）注意流式解析，别一次性读进内存

**完成后的成果（可验证）**
- 拿 4 类样本各一个文件跑解析接口，输出 JSON：
  - docx：段落数、表格数、总字符数与文件实际内容对得上
  - xlsx：sheet 名、行列数、单元格值（含数字/日期类型标注）正确
- 写一个解析耗时日志，为后续 90s 预算留底

---

## 方向 7：异步任务与 API 异步化（赛题硬性要求）

**目标**：长耗时流程走异步，提交立刻返回 `taskId`，前端轮询进度；单文档处理 ≤ 90s。

**大致内容**
- Spring `@Async` + 自定义 `ThreadPoolExecutor`（或消息队列，先不上 MQ）
- 任务表：task_id / 状态(PENDING/RUNNING/SUCCESS/FAILED) / 进度 / 错误信息 / 耗时
- 接口三件套：`POST /api/tasks`（提交，立即返回 taskId）、`GET /api/tasks/{id}`（查状态）、`GET /api/tasks/{id}/result`（取结果）
- 每个阶段打点计时，找出真正耗时的环节

**完成后的成果（可验证）**
```bash
T=$(curl -s -X POST localhost:8080/api/tasks -d '...' | jq -r .taskId)
curl -s localhost:8080/api/tasks/$T      # 期望 RUNNING → SUCCESS
```
提交接口响应时间 < 1s，任务最终 SUCCESS 且 `durationMs` 有记录 → 完成。

---

## 方向 8：向量检索 + LLM 结构化提取

**目标**：从样本集中检索出与模板字段相关的片段，交给大模型抽出结构化字段。

**大致内容**
- 文本切片（chunk 500~800 字 + 重叠）→ 调 Embedding 接口 → 写入 pgvector
- 检索：模板字段名（表头）作为 query，做 `cosine` 相似度 Top-K 召回
- LLM 调用：选一个国内可用的大模型 API（通义/豆包/DeepSeek 等），用 **JSON Schema / function calling** 强制结构化输出
- Prompt 工程：给表头 + 召回片段 + 输出格式约束，要求「找不到就返回 null，不要编造」
- 超时与重试：设 connect/read timeout，失败重试 1 次，避免拖垮 90s 预算

**完成后的成果（可验证）**
- 给定 3 个模板字段，能从 16 个样本文件中召回正确片段 ≥ 2/3
- LLM 输出是可直接 `JSON.parse` 的结构，字段类型与模板一致
- 单字段抽取平均耗时有数字记录

---

## 方向 9：模板表格自动填写

**目标**：上传模板表格（Word/Excel）→ 系统从样本集检索数据 → 填好并导出。

**大致内容**
- 解析模板：识别表头字段、字段单位/类型、待填单元格坐标
- 字段匹配：模板字段 ↔ 样本集抽取结果（先精确/同义匹配，匹配不上再走 LLM 兜底）
- 回填：用 POI 按坐标写回单元格，**保持原模板样式**（字体/边框/合并单元格不能乱）
- 导出：提供下载链接（复用方向 5 的对象存储）

**完成后的成果（可验证）**
- 上传 1 个模板 → 下载到填好的文件 → 与样例表格逐格对比
- 有对比脚本输出「填写准确率」数字（这是评分指标，越早有脚本越好）

---

## 方向 10：性能与准确率达标（评分硬指标）

**目标**：平均响应时间 ≤ 90 秒、信息提取准确率 > 80%。

**大致内容**
- 批量上传时**一次性建索引**，后续 5 次填表复用（不要每次重解析 16 个文件）
- 并行：多字段/多模板字段并发调用 LLM（注意限流）
- 缓存：Embedding 结果缓存、召回结果缓存
- Prompt 多轮迭代 + 少量人工标注样本做回归测试（赛题要求人工标注 + 多轮审核）
- 写 `evaluate` 脚本：自动跑 5 个模板，输出平均准确率 + 平均耗时表

**完成后的成果（可验证）**
- 评测脚本输出类似：`平均准确率 86.4% | 平均响应 38.2s`
- 五项模板每项准确率 ≥ 80%，无单项超时 90s

---

## 方向 11：前后端联调与交付

**目标**：React 前端能完成端到端流程——批量上传、看进度、下载填好的表格。

**大致内容**
- 前端现状：Vite 8 + React 19 + TS，`npm run dev`（pnpm 没装，就用 npm）
- `vite.config.ts` 配 `server.proxy` 转发 `/api` 到 `localhost:8080`，省掉 CORS 麻烦
- 页面：样本集批量上传 → 任务进度轮询 → 模板上传 → 结果预览/下载
- 部署形态任选（Web 即可），确认容器 + 后端 + 前端一键启动

**完成后的成果（可验证）**
- `npm run dev` 打开页面，完整走一遍：传 16 个样本 → 传 1 个模板 → 下载结果
- 全程无需手动改后端代码，Docker 容器在跑

---

## 穿插进行的知识线（不阻塞主线，用到再学）

| 知识点 | 目标 | 大致内容 | 完成后的成果 |
|--------|------|----------|--------------|
| Java 21 语法 | 能用现代写法写业务代码 | `record`、switch 模式匹配、文本块、虚拟线程、Stream/Optional | 项目里 DTO 全用 record，无 lombok 依赖也能保持简洁 |
| Maven 依赖管理 | 加减依赖不踩坑 | `dependency` 作用域、`mvn dependency:tree` 排冲突、`mvnw` 与全局 mvn 区别 | 能自己加一个新依赖并成功编译 |
| Spring Boot 核心 | 理解「为什么加个注解就能跑」 | IoC/DI、自动配置、`@ConfigurationProperties`、profile 多环境 | 能写出 dev/prod 两套配置并切换 |
| RESTful 与接口设计 | 接口规范、好调试 | 路径命名、状态码、分页、参数校验 `@Valid` | 全部接口有统一路径前缀 `/api` 与统一返回体 |
| SQL 与索引 | 检索快、数据不错 | 建表/索引/联合查询、`EXPLAIN`、pgvector 索引（HNSW/IVFFlat） | 万级片段检索在百毫秒级 |
| Docker 与 compose | 环境可复现 | `compose up/down/logs/exec`、卷挂载、healthcheck | 换台机器 `docker compose up -d` 就能复现环境 |

---

## 建议的执行节奏

1. **今天**：方向 0 → 1 → 2（装完 JDK，把 Hello API 跑起来，拿到第一个正反馈）
2. **接下来 2~3 天**：方向 3 → 4 → 5（数据层与存储打通，工程骨架定型）
3. **之后一周**：方向 6 → 7（解析 + 异步，赛题硬性要求落地）
4. **再之后**：方向 8 → 9 → 10（核心 AI 能力与指标冲刺）
5. **收尾**：方向 11（联调、演示、答辩材料）

> 每完成一个方向，立刻把上面总览表的 ☐ 改成 ☑，并在 `.workbuddy/memory/` 的当天日志里记一行结论
> （比如「方向 4 完成：pgvector 已建，库 docai，端口 5432」），避免下次开机重新踩一遍。

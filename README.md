# doc-ai-platform

基于大语言模型的文档理解与多源数据融合系统。把「上传文档 + 一句话需求」变成自动化流水线：
非结构化文档 → 结构化数据资产（字段值 + 原文证据） → 自动填写的业务表格。

## 功能模块

| 模块 | 能力 | 状态 |
|---|---|---|
| 文档解析 | docx / xlsx / md / txt 统一解析，保留标题路径、表格坐标与单元格类型 | 已实现 |
| 批量建库 | 多文件一次上传，异步解析 + 切片 + 向量入库 | 已实现（需数据库） |
| 跨文档检索 | 向量相似度 + 关键词双路召回 | 已实现（向量需 Embedding Key） |
| 信息提取 | 字段定义 → 检索 → LLM 抽取 → 校验归一 → 带证据入库 → 人工复核 | 已实现（真实抽取需 LLM Key） |
| 模板填表 | 表头识别 → 字段映射 → 检索填充 → 按坐标保真回填 → 下载 | 已实现 |
| 文档智能操作 | 自然语言指令 → 规则解析 → 白名单校验 → 副本试执行 → 预览 → 版本化与回滚 | 已实现（仅 docx） |
| 异步任务 | 提交返回 taskId，支持轮询与 SSE 进度推送 | 已实现（需数据库） |
| 评测脚本 | 批量跑模板，统计平均准确率与响应时间 | 已实现 |

## 目录结构

```
backend/             Spring Boot 4.1.2 + Java 21
  src/main/java/cn/edu/zjut/backend/
    controller/      接口层
    service/         业务层（parse / fill / docop / extract / llm / embedding）
    dao/             pgvector 向量读写
    entity/ mapper/  MyBatis-Plus 数据访问
frontend/            React 19 + TypeScript + Vite
  src/App.tsx        单页控制台：建库 / 填表 / 文档操作
  nginx.conf         生产环境静态资源 + /api 反代（含 SSE 配置）
sql/init/            001 建表、002 向量维度升级
scripts/evaluate.py  准确率与耗时评测
docs/                开发总纲与待办清单
```

## 快速开始

### 方式一：Docker Compose（全栈）

```bash
# 1. 准备环境变量（含模型 Key，可为空）
cp .env.example .env

# 2. 启动数据库 / 对象存储 / 缓存 / 后端 / 前端
docker compose up -d

# 3. 访问
#    前端  http://localhost:8081
#    后端  http://localhost:8080/api/health
```

已有数据卷时，向量维度升级需手动执行一次：

```bash
docker exec -i docai-postgres psql -U docai -d docai < sql/init/002_alter_embedding_1536.sql
```

### 方式二：本地开发

```bash
# 依赖容器
docker compose up -d postgres silo redis

# 后端（沙箱内 spring-boot:run 端口会被注入，建议打包后显式指定端口）
cd backend
./mvnw.cmd package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar --server.port=8080

# 前端
cd frontend
npm install
npm run dev        # http://localhost:5173，/api 自动代理到 8080
```

## 配置说明

敏感配置全部通过环境变量注入，**代码与仓库中不含任何真实 Key**。

| 变量 | 说明 | 留空时行为 |
|---|---|---|
| `LLM_API_KEY` | DeepSeek 对话 Key | 降级 Mock，抽取返回 null，链路仍可走通 |
| `EMBEDDING_API_KEY` | 通义 DashScope Key（text-embedding-v3） | 跳过向量化，检索降级关键词召回 |
| `SPRING_DATASOURCE_*` | 数据库连接 | 默认 localhost:5432/docai |
| `MINIO_*` | 对象存储 | 默认 localhost:9000 |

填入方式（任选其一）：

1. 项目根 `.env`（`cp .env.example .env` 后填写，已被 `.gitignore` 排除）
2. `backend/src/main/resources/application-local.yaml` + `--spring.profiles.active=local`
3. 系统环境变量

> 说明：DeepSeek 官方仅提供对话模型（`deepseek-flash` / `deepseek-v4-pro`），**无 Embedding 接口**，
> 因此向量化使用通义 `text-embedding-v3`（1536 维），`doc_chunk.embedding` 对应 `vector(1536)`。

## 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/db/ping` | 数据库与 pgvector 探活 |
| POST | `/api/ingest` | 批量上传样本（multipart `files`），返回 taskId |
| POST | `/api/parse` | 单文件解析 |
| POST | `/api/files` | 单文件上传到对象存储 |
| GET | `/api/tasks/{id}` | 任务状态（含进度、结果） |
| GET | `/api/tasks/{id}/stream` | SSE 进度推送 |
| POST | `/api/tasks/extract` | 异步抽取任务 |
| POST | `/api/tasks/fill` | 异步填表任务 |
| GET | `/api/tasks/{id}/file` | 下载任务产物 |
| GET/POST/PUT/DELETE | `/api/fields` | 抽取字段定义管理 |
| POST | `/api/extract` | 直接对给定上下文抽取 |
| POST | `/api/extract/corpus` | 从样本集检索后抽取 |
| POST | `/api/extract/persist` | 抽取 + 校验 + 带证据入库 |
| GET | `/api/extract/results?documentId=` | 查询抽取结果 |
| POST | `/api/extract/results/{id}/review` | 人工确认 / 驳回 |
| POST | `/api/fill` | 模板填表（返回字段值明细） |
| POST | `/api/fill/export` | 模板填表并下载成品（保留原样式） |
| POST | `/api/doc/preview` | 文档指令试执行预览 |
| GET | `/api/doc/preview/{token}/download` | 下载预览文件 |
| POST | `/api/doc/apply` | 确认执行并保存新版本 |
| GET | `/api/doc/{docId}/versions` | 版本列表 |
| POST | `/api/doc/{docId}/rollback` | 回滚到历史版本 |
| GET | `/api/doc/{docId}/versions/{v}/download` | 下载指定版本 |

统一返回体：`{ "code": 0, "message": "success", "data": ... }`，`code != 0` 即失败。

## 测试与评测

```bash
cd backend && ./mvnw.cmd test        # 单元测试（不依赖数据库/外部服务）
python scripts/evaluate.py \
  --samples "D:/samples/*.docx" \
  --templates "D:/templates/*.xlsx" \
  --answers "D:/answers/t1.json"
```

评测输出平均准确率与平均响应时间，对照赛题指标（准确率 > 80%、单表 ≤ 90 秒）。

## 已知限制

- 文档智能操作目前仅支持 `.docx`；表格结构增删列不在白名单内。
- 文本替换按文本块（run）匹配，跨文本块拆分的词无法命中，执行报告会如实标注。
- 抽取校验依赖「值能在证据原文中找到」，跨段落拼接的答案会被判为待复核。
- 向量检索需配置 Embedding Key；未配置时仅关键词召回可用。
- 文档版本按文件名累积；服务无状态，连续编辑需上传最新版本文件，不会自动串联版本链。
- 版本库与导出文件存本地 `data/` 目录，多实例部署需替换为共享存储。

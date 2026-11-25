# API 接口规范

## 一、 统一响应格式 (Result Wrapper)

无论后端内部怎么处理，**所有 HTTP 接口必须返回统一的 `Result` 结构**（文件下载和 SSE 除外）。前端 Axios 拦截器将依赖此结构。

```json
{
  "code": 200,          // 业务状态码（见下文），非 HTTP 状态码
  "message": "success", // 提示信息
  "data": { ... },      // 具体业务数据，可以是对象、数组或 null
  "traceId": "a1b2c3"   // 请求追踪 ID（可选，便于排查 Bug）
}
```

**业务状态码（code）规范**：
*   `200`：成功。
*   `400`：参数校验失败。
*   `401`：未登录或 Token 过期。
*   `403`：越权访问（如跨部门访问、处理员试图建任务）。
*   `404`：资源不存在。
*   `500`：系统内部异常（如数据库宕机、AI 接口超时）。

---

## 二、 URL 路径设计规范

1. **使用名词复数表示资源集合**，使用层级关系表示从属。
2. **统一前缀**：所有内部接口统一以 `/api/v1` 开头；外部 API 统一以 `/openapi/v1` 开头。
3. **动作解耦**：尽量用 HTTP 方法表达动作；实在无法用标准方法表达的复杂动作，使用 `POST /资源/动作`。

| 场景 | 正确示例 | 错误示例（严禁使用） |
| :--- | :--- | :--- |
| 获取任务列表 | `GET /api/v1/tasks` | `GET /api/v1/getTaskList` |
| 获取单条任务 | `GET /api/v1/tasks/{id}` | `GET /api/v1/task?id=1` |
| 创建任务 | `POST /api/v1/tasks` | `POST /api/v1/createTask` |
| 分配任务 (复杂动作) | `POST /api/v1/tasks/{id}/assign` | `POST /api/v1/assignTask` |
| 获取某任务的源文件 | `GET /api/v1/tasks/{id}/documents` | `GET /api/v1/documentListByTask` |

---

## 三、 HTTP 方法语义

*   `GET`：只读查询，**严禁**修改数据。
*   `POST`：创建新资源，或执行复杂动作。
*   `PUT`：全量更新资源（如修改活文档 JSON）。
*   `PATCH`：局部更新（如只修改任务状态）。
*   `DELETE`：删除资源（底层逻辑为软删除）。

---

## 四、 参数传递规范

1. **路径参数 (Path Variable)**：用于定位具体资源，如 `/tasks/{taskId}`。
2. **查询参数 (Query Parameter)**：用于分页、排序、过滤（GET 请求），如 `/documents?deptId=1&category=SOURCE`。
3. **请求体 (Request Body)**：用于 POST/PUT/PATCH，统一使用 **JSON** 格式（文件上传除外）。

---

## 五、 分页规范

由于系统中的文件池、任务池必然面临大数据量，必须统一分页参数和返回结构。

**请求参数**：
*   `pageNo`：当前页码，从 1 开始（默认 1）。
*   `pageSize`：每页条数（默认 10，最大 100）。

**返回结构（包含在 `data` 字段内）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 150,       // 总记录数
    "pageNo": 1,        // 当前页码
    "pageSize": 10,     // 每页大小
    "list": [ ... ]     // 当前页数据列表
  }
}
```

## 六、邮件发现与附件导入

邮件接口仅允许当前部门的 `ADMIN`、`DISPATCHER` 调用。查询接口只保存邮件正文文本和附件元信息，不读取附件二进制；附件只有在用户明确选择后才会从 IMAP 读取并写入 MinIO。

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `POST` | `/api/v1/emails/discover` | 手动查询当前部门启用邮箱的新邮件，并保存元信息 |
| `GET` | `/api/v1/emails?status=UNPROCESSED` | 查询已发现邮件，可按处理状态筛选 |
| `GET` | `/api/v1/emails/{emailId}` | 查询邮件正文及附件元信息 |
| `POST` | `/api/v1/emails/{emailId}/attachments/import` | 按 `attachmentIds` 导入选中附件并创建文档版本 |
| `POST` | `/api/v1/emails/{emailId}/attachments/{attachmentId}/ignore` | 忽略单个附件 |
| `POST` | `/api/v1/emails/{emailId}/ignore` | 忽略邮件及尚未处理的附件 |

邮件状态为 `UNPROCESSED`、`PROCESSED`、`IGNORED`；附件状态还包括处理中、导入失败等内部状态。成功导入的附件通过 `document_version_id` 追溯对应版本。
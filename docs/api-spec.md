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
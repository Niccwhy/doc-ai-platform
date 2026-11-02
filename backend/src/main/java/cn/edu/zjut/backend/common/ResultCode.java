package cn.edu.zjut.backend.common;

/**
 * 通用错误码枚举。
 */
public enum ResultCode {

    SUCCESS(0, "success"),

    // 4xx 客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 5xx 服务端错误
    INTERNAL_ERROR(500, "服务器内部错误"),
    BUSINESS_ERROR(600, "业务处理失败"),

    // 业务扩展码（按需追加）
    TASK_NOT_FOUND(1001, "任务不存在"),
    TASK_FAILED(1002, "任务执行失败"),
    FILE_PARSE_ERROR(1003, "文件解析失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

}

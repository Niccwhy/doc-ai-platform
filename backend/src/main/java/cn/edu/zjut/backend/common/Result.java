package cn.edu.zjut.backend.common;

/**
 * 统一返回体。
 * <p>
 * 约定：
 * <ul>
 *   <li>code == 0 表示成功，其余为错误码（见 {@link ResultCode}）</li>
 *   <li>成功时 message 固定为 "success"，失败时携带具体错误信息</li>
 *   <li>data 为业务数据，失败时为 null</li>
 * </ul>
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.code(), ResultCode.SUCCESS.message(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.code(), resultCode.message(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.code(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

}

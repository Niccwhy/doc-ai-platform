package cn.edu.zjut.backend.common;

/**
 * 业务异常。业务层抛出，由 {@link GlobalExceptionHandler} 统一捕获并转成统一返回体。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.message());
        this.code = resultCode.code();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.code();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}

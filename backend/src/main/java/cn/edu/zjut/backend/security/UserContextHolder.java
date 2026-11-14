package cn.edu.zjut.backend.security;

import cn.edu.zjut.backend.exception.BizException;

/**
 * 基于 ThreadLocal 保存当前请求的用户上下文，由 JWT 过滤器写入并在请求结束后清理。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static UserContext getRequired() {
        UserContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(401, "未登录或 Token 过期");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

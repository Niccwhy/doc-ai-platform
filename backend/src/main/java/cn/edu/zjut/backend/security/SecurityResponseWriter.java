package cn.edu.zjut.backend.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * 安全过滤器链中无法经过 Controller 时，直接输出统一 Result 结构。
 */
final class SecurityResponseWriter {

    private SecurityResponseWriter() {
    }

    static void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code
                + ",\"message\":\"" + message
                + "\",\"data\":null,\"traceId\":\"" + UUID.randomUUID() + "\"}");
    }
}

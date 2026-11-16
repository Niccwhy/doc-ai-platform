package cn.edu.zjut.backend.security;

/**
 * 当前请求的用户上下文。
 */
public record UserContext(Long userId, Long deptId, String username, String role) {
}

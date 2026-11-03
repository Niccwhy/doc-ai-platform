package cn.edu.zjut.backend.dto;

/**
 * 更新用户请求，字段为 null 表示不修改。
 */
public record UserUpdateRequest(
        String realName,
        String email,
        String role,
        String status
) {
}

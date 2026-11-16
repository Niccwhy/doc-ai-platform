package cn.edu.zjut.backend.auth.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        Long deptId,
        String username,
        String realName,
        String role) {
}

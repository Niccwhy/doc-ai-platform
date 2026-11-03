package cn.edu.zjut.backend.dto;

/**
 * 登录响应：令牌 + 有效期（秒）+ 用户信息。
 */
public record LoginResponse(
        String token,
        long expiresIn,
        UserInfo user
) {
}

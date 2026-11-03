package cn.edu.zjut.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 * <p>
 * 密钥从配置 `jwt.secret` 读取，必须 >= 32 字节（HS256 要求）；
 * 生产环境请通过环境变量 JWT_SECRET 覆盖默认值。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-minutes:720}") long expirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 字节，无法安全用于 HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    /**
     * 签发令牌，携带用户 id、部门、角色。
     */
    public String generate(UserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("username", principal.getUsername())
                .claim("deptId", principal.getDeptId())
                .claim("role", principal.getRole())
                .claim("realName", principal.getRealName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌，失败抛 JwtException（由过滤器捕获后按未认证处理）。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 由 claims 还原主体（无状态，不查库）。
     * <p>
     * 说明：账号被禁用后旧令牌在有效期内仍可用；如需即时失效，
     * 可在过滤器中补一次状态查询或引入令牌黑名单。
     */
    public UserPrincipal toPrincipal(Claims claims) {
        Long id = Long.valueOf(claims.getSubject());
        Object deptClaim = claims.get("deptId");
        Object roleClaim = claims.get("role");
        if (!(deptClaim instanceof Number deptNumber) || roleClaim == null) {
            throw new IllegalArgumentException("令牌缺少 deptId 或 role");
        }
        String username = claims.get("username", String.class);
        String realName = claims.get("realName", String.class);
        return new UserPrincipal(id, deptNumber.longValue(), username, null,
                String.valueOf(roleClaim), realName, true);
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }
}

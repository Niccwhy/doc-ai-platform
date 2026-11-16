package cn.edu.zjut.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_DEPT_ID = "deptId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.getExpirationMinutes() * 60;
    }

    public String generateToken(UserContext context) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(context.username())
                .claim(CLAIM_USER_ID, context.userId())
                .claim(CLAIM_DEPT_ID, context.deptId())
                .claim(CLAIM_ROLE, context.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public UserContext parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null || role.isBlank()) {
            throw new JwtException("Token 缺少声明: " + CLAIM_ROLE);
        }
        return new UserContext(
                toLong(claims.get(CLAIM_USER_ID), CLAIM_USER_ID),
                toLong(claims.get(CLAIM_DEPT_ID), CLAIM_DEPT_ID),
                claims.getSubject(),
                role
        );
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private static Long toLong(Object value, String claimName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new JwtException("Token 缺少声明: " + claimName);
    }
}

package cn.edu.zjut.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefgh";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMinutes(30);
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void shouldGenerateAndParseToken() {
        UserContext context = new UserContext(11L, 22L, "alice", "PROCESSOR");

        String token = jwtTokenProvider.generateToken(context);

        assertThat(jwtTokenProvider.parseToken(token)).isEqualTo(context);
        assertThat(jwtTokenProvider.getExpirationSeconds()).isEqualTo(1800);
    }

    @Test
    void shouldRejectExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("alice")
                .claim("uid", 11L)
                .claim("deptId", 22L)
                .claim("role", "PROCESSOR")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtTokenProvider.generateToken(new UserContext(1L, 2L, "bob", "ADMIN"));
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThatThrownBy(() -> jwtTokenProvider.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenWithoutRequiredClaims() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("alice")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseToken(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Token 缺少声明");
    }
}

package cn.edu.zjut.backend;

import cn.edu.zjut.backend.security.JwtService;
import cn.edu.zjut.backend.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与解析单元测试（纯逻辑，不依赖 Spring 与数据库）。
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void generateAndParseRoundTrip() {
        UserPrincipal principal = new UserPrincipal(7L, 3L, "alice", "hash",
                "DISPATCHER", "分配员A", true);

        String token = jwtService.generate(principal);
        Claims claims = jwtService.parse(token);
        UserPrincipal parsed = jwtService.toPrincipal(claims);

        assertThat(parsed.getId()).isEqualTo(7L);
        assertThat(parsed.getDeptId()).isEqualTo(3L);
        assertThat(parsed.getUsername()).isEqualTo("alice");
        assertThat(parsed.getRole()).isEqualTo("DISPATCHER");
        assertThat(parsed.getRealName()).isEqualTo("分配员A");
        assertThat(parsed.getAuthorities()).extracting("authority").containsExactly("ROLE_DISPATCHER");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generate(new UserPrincipal(1L, 1L, "admin", "hash",
                "ADMIN", "管理员", true));
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void expirationIsExposedInSeconds() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600);
    }
}

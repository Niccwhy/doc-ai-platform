package cn.edu.zjut.backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-abcdefgh");
        properties.setExpirationMinutes(30);
        jwtTokenProvider = new JwtTokenProvider(properties);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, new RestAuthenticationEntryPoint());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInjectUserContextForValidToken() throws Exception {
        String token = jwtTokenProvider.generateToken(new UserContext(7L, 8L, "alice", "PROCESSOR"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<UserContext> contextDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> contextDuringChain.set(UserContextHolder.get()));

        assertThat(contextDuringChain.get()).isEqualTo(new UserContext(7L, 8L, "alice", "PROCESSOR"));
        assertThat(UserContextHolder.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401");
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void shouldContinueChainWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainInvoked.set(true);
            assertThat(UserContextHolder.get()).isNull();
        });

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}

package cn.edu.zjut.backend.auth;

import cn.edu.zjut.backend.auth.dto.LoginRequest;
import cn.edu.zjut.backend.auth.dto.LoginResponse;
import cn.edu.zjut.backend.auth.entity.SysUser;
import cn.edu.zjut.backend.auth.mapper.SysUserMapper;
import cn.edu.zjut.backend.auth.service.AuthService;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.JwtProperties;
import cn.edu.zjut.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-abcdefgh");
        properties.setExpirationMinutes(60);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(properties);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(sysUserMapper, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void shouldLoginWithCorrectCredentials() {
        SysUser user = buildUser();
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        LoginResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.deptId()).isEqualTo(2L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo("PROCESSOR");
    }

    @Test
    void shouldRejectUnknownUser() {
        LoginRequest request = new LoginRequest("nobody", "password123");

        BizException exception = catchThrowableOfType(() -> authService.login(request), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(401);
    }

    @Test
    void shouldRejectWrongPassword() {
        SysUser user = buildUser();
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BizException exception = catchThrowableOfType(
                () -> authService.login(new LoginRequest("alice", "wrong-password")), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(401);
    }

    @Test
    void shouldRejectDeletedUser() {
        SysUser user = buildUser();
        user.setDeletedAt(LocalDateTime.now());
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BizException exception = catchThrowableOfType(
                () -> authService.login(new LoginRequest("alice", "password123")), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(401);
    }

    @Test
    void shouldRejectDisabledUser() {
        SysUser user = buildUser();
        user.setStatus(0);
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BizException exception = catchThrowableOfType(
                () -> authService.login(new LoginRequest("alice", "password123")), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(403);
    }

    private SysUser buildUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setDeptId(2L);
        user.setUsername("alice");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRealName("Alice");
        user.setRole("PROCESSOR");
        user.setStatus(1);
        return user;
    }
}

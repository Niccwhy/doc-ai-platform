package cn.edu.zjut.backend.auth.service;

import cn.edu.zjut.backend.auth.dto.LoginRequest;
import cn.edu.zjut.backend.auth.dto.LoginResponse;
import cn.edu.zjut.backend.auth.entity.SysUser;
import cn.edu.zjut.backend.auth.mapper.SysUserMapper;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.JwtTokenProvider;
import cn.edu.zjut.backend.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null || user.getDeletedAt() != null) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(403, "账号已被禁用");
        }
        UserContext userContext = new UserContext(
                user.getId(), user.getDeptId(), user.getUsername(), user.getRole());
        String token = jwtTokenProvider.generateToken(userContext);
        return new LoginResponse(token, "Bearer", jwtTokenProvider.getExpirationSeconds(),
                user.getId(), user.getDeptId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}

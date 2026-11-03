package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.LoginRequest;
import cn.edu.zjut.backend.dto.LoginResponse;
import cn.edu.zjut.backend.dto.UserInfo;
import cn.edu.zjut.backend.entity.SysUser;
import cn.edu.zjut.backend.mapper.SysUserMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import cn.edu.zjut.backend.security.JwtService;
import cn.edu.zjut.backend.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录签发令牌、查询当前用户。
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 登录：校验密码与账号状态，签发 JWT。
     * 用户不存在与密码错误返回同一提示，避免账号枚举。
     */
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", request.username())
                .isNull("deleted_at"));
        // 密码为空时直接走统一「用户名或密码错误」，避免 matches(null, ...) 抛 NPE
        if (user == null || request.password() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getDeptId(),
                user.getUsername(), user.getPasswordHash(), user.getRole(),
                user.getRealName(), true);
        String token = jwtService.generate(principal);
        return new LoginResponse(token, jwtService.getExpirationSeconds(), UserInfo.from(user));
    }

    /**
     * 当前登录用户（从库中读取最新状态，避免令牌中的角色过期）。
     */
    public UserInfo me() {
        SysUser user = userMapper.selectById(CurrentUser.id());
        if (user == null || user.getDeletedAt() != null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在或已注销");
        }
        return UserInfo.from(user);
    }
}

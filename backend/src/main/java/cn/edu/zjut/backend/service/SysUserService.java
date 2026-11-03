package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.UserCreateRequest;
import cn.edu.zjut.backend.dto.UserInfo;
import cn.edu.zjut.backend.dto.UserUpdateRequest;
import cn.edu.zjut.backend.entity.SysUser;
import cn.edu.zjut.backend.mapper.SysUserMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 用户管理。
 * <ul>
 *   <li>系统管理员：可管理全部门用户</li>
 *   <li>分配员：可查看本部门处理员（用于派发）</li>
 * </ul>
 */
@Service
public class SysUserService {

    private static final Set<String> ROLES = Set.of("ADMIN", "DISPATCHER", "PROCESSOR");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户列表。ADMIN 可按 deptId 过滤，其余角色强制本部门。
     */
    public List<UserInfo> list(String role, Long deptId, String keyword) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>().isNull("deleted_at");
        if (CurrentUser.isAdmin()) {
            if (deptId != null) {
                wrapper.eq("dept_id", deptId);
            }
        } else {
            wrapper.eq("dept_id", CurrentUser.deptId());
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq("role", role);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("username", keyword).or().like("real_name", keyword));
        }
        wrapper.orderByAsc("id");
        return userMapper.selectList(wrapper).stream().map(UserInfo::from).toList();
    }

    public UserInfo create(UserCreateRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度至少 6 位");
        }
        validateRole(request.role());

        Long exists = userMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("username", request.username().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在");
        }

        // 非管理员只能在本部门内建用户；管理员可指定 deptId
        Long deptId;
        if (CurrentUser.isAdmin()) {
            deptId = request.deptId() == null ? CurrentUser.deptId() : request.deptId();
        } else {
            if (request.deptId() != null && !request.deptId().equals(CurrentUser.deptId())) {
                throw new BizException(ResultCode.FORBIDDEN, "只能在本部门内创建用户");
            }
            deptId = CurrentUser.deptId();
        }
        SysUser user = new SysUser();
        user.setDeptId(deptId);
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setRole(request.role());
        user.setStatus("ACTIVE");
        user.setEmail(request.email());
        user.setCreatedAt(OffsetDateTime.now());
        userMapper.insert(user);
        return UserInfo.from(user);
    }

    public UserInfo update(Long id, UserUpdateRequest request) {
        SysUser user = require(id);
        if (request.realName() != null) {
            user.setRealName(request.realName());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.role() != null) {
            validateRole(request.role());
            user.setRole(request.role());
        }
        if (request.status() != null) {
            if (!"ACTIVE".equals(request.status()) && !"DISABLED".equals(request.status())) {
                throw new BizException(ResultCode.BAD_REQUEST, "状态仅支持 ACTIVE / DISABLED");
            }
            user.setStatus(request.status());
        }
        userMapper.updateById(user);
        return UserInfo.from(user);
    }

    public void resetPassword(Long id, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度至少 6 位");
        }
        SysUser user = require(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userMapper.updateById(user);
    }

    /**
     * 软删除。不允许删除自己，避免把自己锁在系统外。
     */
    public void delete(Long id) {
        if (id.equals(CurrentUser.id())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除当前登录账号");
        }
        SysUser user = require(id);
        user.setDeletedAt(OffsetDateTime.now());
        user.setStatus("DISABLED");
        userMapper.updateById(user);
    }

    private SysUser require(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!CurrentUser.isAdmin() && !user.getDeptId().equals(CurrentUser.deptId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能管理本部门用户");
        }
        return user;
    }

    private void validateRole(String role) {
        if (role == null || !ROLES.contains(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色仅支持 ADMIN / DISPATCHER / PROCESSOR");
        }
    }
}

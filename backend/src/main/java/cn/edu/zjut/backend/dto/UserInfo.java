package cn.edu.zjut.backend.dto;

import cn.edu.zjut.backend.entity.SysUser;

/**
 * 登录用户信息（不含密码）。
 */
public record UserInfo(
        Long id,
        String username,
        String realName,
        String role,
        Long deptId
) {

    public static UserInfo from(SysUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getDeptId());
    }
}

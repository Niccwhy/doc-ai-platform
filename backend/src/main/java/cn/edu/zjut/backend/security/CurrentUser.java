package cn.edu.zjut.backend.security;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文。业务层通过它取用户 id / 部门 id，实现租户隔离。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        return principal;
    }

    public static Long id() {
        return principal().getId();
    }

    public static Long deptId() {
        return principal().getDeptId();
    }

    public static String role() {
        return principal().getRole();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }
}

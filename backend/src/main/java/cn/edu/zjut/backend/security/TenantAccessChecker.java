package cn.edu.zjut.backend.security;

import cn.edu.zjut.backend.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class TenantAccessChecker {

    public void requireAnyRole(String... roles) {
        UserContext context = UserContextHolder.getRequired();
        if (roles == null || Arrays.stream(roles).noneMatch(role -> role.equals(context.role()))) {
            throw new BizException(403, "无权执行该操作");
        }
    }

    public void requireSameDepartment(Long resourceDeptId) {
        UserContext context = UserContextHolder.getRequired();
        if (resourceDeptId == null || context.deptId() == null
                || !resourceDeptId.equals(context.deptId())) {
            throw new BizException(403, "无权访问该部门资源");
        }
    }
}

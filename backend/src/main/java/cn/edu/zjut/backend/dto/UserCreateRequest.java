package cn.edu.zjut.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建用户请求。deptId 为空时默认当前用户所属部门。
 */
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        String realName,
        @NotBlank(message = "角色不能为空") String role,
        String email,
        Long deptId
) {
}

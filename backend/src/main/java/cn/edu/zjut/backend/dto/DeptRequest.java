package cn.edu.zjut.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 部门创建/重命名请求。
 */
public record DeptRequest(
        @NotBlank(message = "部门名称不能为空") String name,
        Long parentId
) {
}

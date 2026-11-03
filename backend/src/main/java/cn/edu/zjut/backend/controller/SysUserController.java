package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.UserCreateRequest;
import cn.edu.zjut.backend.dto.UserInfo;
import cn.edu.zjut.backend.dto.UserUpdateRequest;
import cn.edu.zjut.backend.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口。
 * <ul>
 *   <li>列表：管理员看全部（可按部门过滤），分配员看本部门（用于挑选处理员）</li>
 *   <li>增删改：仅系统管理员</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public Result<List<UserInfo>> list(@RequestParam(required = false) String role,
                                       @RequestParam(required = false) Long deptId,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(userService.list(role, deptId, keyword));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserInfo> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserInfo> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}

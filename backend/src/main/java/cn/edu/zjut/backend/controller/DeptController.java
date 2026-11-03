package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.DeptRequest;
import cn.edu.zjut.backend.entity.SysDept;
import cn.edu.zjut.backend.service.DeptService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理接口。查询：登录用户；增删改：系统管理员。
 */
@RestController
@RequestMapping("/api/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @GetMapping
    public Result<List<SysDept>> list() {
        return Result.ok(deptService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysDept> create(@Valid @RequestBody DeptRequest request) {
        return Result.ok(deptService.create(request.name(), request.parentId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysDept> rename(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        return Result.ok(deptService.rename(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}

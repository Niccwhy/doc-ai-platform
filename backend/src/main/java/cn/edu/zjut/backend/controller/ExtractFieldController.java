package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.service.ExtractFieldService;
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
 * 抽取字段定义管理接口。
 */
@RestController
@RequestMapping("/api/fields")
public class ExtractFieldController {

    private final ExtractFieldService fieldService;

    public ExtractFieldController(ExtractFieldService fieldService) {
        this.fieldService = fieldService;
    }

    @GetMapping
    public Result<List<ExtractField>> list() {
        return Result.ok(fieldService.list());
    }

    @PostMapping
    public Result<ExtractField> create(@RequestBody ExtractField field) {
        return Result.ok(fieldService.create(field));
    }

    @PutMapping("/{id}")
    public Result<ExtractField> update(@PathVariable Long id, @RequestBody ExtractField field) {
        return Result.ok(fieldService.update(id, field));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fieldService.delete(id);
        return Result.ok();
    }
}

package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.entity.ExtractSchema;
import cn.edu.zjut.backend.service.ExtractSchemaService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 抽取 Schema 管理接口（替代 v1 的 /api/fields）。
 * 字段定义存于 schema_json，支持自然语言描述生成。
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

    private final ExtractSchemaService schemaService;

    public SchemaController(ExtractSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * 列出本部门 schema。
     */
    @GetMapping
    public Result<List<ExtractSchema>> list() {
        return Result.ok(schemaService.list());
    }

    /**
     * 查询单个 schema 及其字段。
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        ExtractSchema schema = schemaService.get(id);
        return Result.ok(Map.of(
                "schema", schema,
                "fields", schemaService.fieldsOf(schema)
        ));
    }

    /**
     * 创建 schema。
     * body: { "name": "合同抽取", "promptText": "抽取合同类字段", "fields": [{...}] }
     */
    @PostMapping
    public Result<ExtractSchema> create(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", ""));
        String promptText = body.get("promptText") == null ? null : String.valueOf(body.get("promptText"));
        List<ExtractField> fields = parseFields(body.get("fields"));
        return Result.ok(schemaService.create(name, promptText, fields));
    }

    /**
     * 更新 schema（字段定义生成新版本）。
     */
    @PutMapping("/{id}")
    public Result<ExtractSchema> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String promptText = body.get("promptText") == null ? null : String.valueOf(body.get("promptText"));
        List<ExtractField> fields = body.containsKey("fields") ? parseFields(body.get("fields")) : null;
        return Result.ok(schemaService.update(id, name, promptText, fields));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        schemaService.delete(id);
        return Result.ok();
    }

    /**
     * 从 JSON 反序列化字段列表（借助 ObjectMapper 在 service 层完成更稳妥，
     * 这里直接依赖 Jackson 将 List<Map> 转 List<ExtractField>）。
     */
    @SuppressWarnings("unchecked")
    private List<ExtractField> parseFields(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        return list.stream().map(o -> om.convertValue(o, ExtractField.class)).toList();
    }
}

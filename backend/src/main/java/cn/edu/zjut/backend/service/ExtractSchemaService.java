package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.entity.ExtractSchema;
import cn.edu.zjut.backend.mapper.ExtractSchemaMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 抽取 Schema 服务：管理字段定义（存于 extract_schema.schema_json）。
 *
 * <p>替代 v1 的字段表（extract_field）语义：字段定义作为 JSON 数组存于 schema，
 * 支持「自然语言描述 → 生成字段定义」的赛题要求。
 */
@Service
public class ExtractSchemaService {

    private static final Set<String> ALLOWED_TYPES = Set.of("string", "number", "date", "boolean", "enum");
    private static final Set<String> ALLOWED_STATUS = Set.of("DRAFT", "ACTIVE", "ARCHIVED");

    private final ExtractSchemaMapper schemaMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtractSchemaService(ExtractSchemaMapper schemaMapper) {
        this.schemaMapper = schemaMapper;
    }

    /**
     * 列出本部门的 schema。
     */
    public List<ExtractSchema> list() {
        return schemaMapper.selectList(new QueryWrapper<ExtractSchema>()
                .eq("dept_id", CurrentUser.deptId())
                .isNull("deleted_at")
                .orderByDesc("id"));
    }

    /**
     * 获取某个 schema（含租户校验）。
     */
    public ExtractSchema get(Long id) {
        ExtractSchema schema = schemaMapper.selectById(id);
        if (schema == null || schema.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "Schema 不存在");
        }
        if (!schema.getDeptId().equals(CurrentUser.deptId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问其他部门的 Schema");
        }
        return schema;
    }

    /**
     * 创建 schema：字段定义列表序列化为 schema_json。
     */
    public ExtractSchema create(String name, String promptText, List<ExtractField> fields) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "Schema 名称不能为空");
        }
        List<ExtractField> normalized = normalizeFields(fields);
        if (normalized.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少需要一个字段");
        }

        ExtractSchema schema = new ExtractSchema();
        schema.setDeptId(CurrentUser.deptId());
        schema.setCreatorId(CurrentUser.id());
        schema.setName(name.trim());
        schema.setPromptText(promptText);
        schema.setSchemaJson(toFieldsJson(normalized));
        schema.setVersion(1);
        schema.setStatus("ACTIVE");
        schema.setCreatedAt(OffsetDateTime.now());
        schemaMapper.insert(schema);
        return schema;
    }

    /**
     * 更新 schema 的字段定义（生成新版本号）。
     */
    public ExtractSchema update(Long id, String name, String promptText, List<ExtractField> fields) {
        ExtractSchema schema = get(id);
        if (name != null && !name.isBlank()) {
            schema.setName(name.trim());
        }
        if (promptText != null) {
            schema.setPromptText(promptText);
        }
        if (fields != null) {
            List<ExtractField> normalized = normalizeFields(fields);
            if (normalized.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "至少需要一个字段");
            }
            schema.setSchemaJson(toFieldsJson(normalized));
            schema.setVersion(schema.getVersion() == null ? 2 : schema.getVersion() + 1);
        }
        schema.setUpdatedAt(OffsetDateTime.now());
        schemaMapper.updateById(schema);
        return schema;
    }

    /**
     * 删除（软删）schema。
     */
    public void delete(Long id) {
        ExtractSchema schema = get(id);
        schema.setDeletedAt(OffsetDateTime.now());
        schemaMapper.updateById(schema);
    }

    /**
     * 解析 schema 的字段定义。
     */
    public List<ExtractField> fieldsOf(ExtractSchema schema) {
        return parseFields(schema.getSchemaJson());
    }

    /**
     * 从激活 schema 中取全部字段（供抽取管线使用）。
     */
    public List<ExtractField> activeFields() {
        List<ExtractSchema> schemas = schemaMapper.selectList(new QueryWrapper<ExtractSchema>()
                .eq("dept_id", CurrentUser.deptId())
                .eq("status", "ACTIVE")
                .isNull("deleted_at")
                .orderByDesc("id"));
        List<ExtractField> result = new ArrayList<>();
        for (ExtractSchema schema : schemas) {
            result.addAll(parseFields(schema.getSchemaJson()));
        }
        return result;
    }

    // ---------- 字段序列化 ----------

    private String toFieldsJson(List<ExtractField> fields) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> arr = new ArrayList<>();
        for (ExtractField f : fields) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", f.getKey());
            item.put("label", f.getName());
            item.put("type", f.getDataType());
            item.put("unit", f.getUnit());
            item.put("required", false);
            item.put("description", f.getDescription());
            if (f.getAlias() != null && !f.getAlias().isBlank()) {
                item.put("alias", f.getAlias());
            }
            arr.add(item);
        }
        root.put("fields", arr);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "序列化字段定义失败: " + e.getMessage());
        }
    }

    private List<ExtractField> parseFields(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(schemaJson, new TypeReference<>() {
            });
            Object rawFields = root.get("fields");
            if (!(rawFields instanceof List<?> rawList)) {
                return List.of();
            }
            List<ExtractField> result = new ArrayList<>();
            for (Object o : rawList) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                ExtractField f = new ExtractField();
                f.setKey(str(m.get("key")));
                f.setName(str(m.get("label")));
                f.setDataType(str(m.get("type")));
                f.setUnit(str(m.get("unit")));
                f.setDescription(str(m.get("description")));
                f.setAlias(str(m.get("alias")));
                result.add(f);
            }
            return result;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "解析字段定义失败: " + e.getMessage());
        }
    }

    private List<ExtractField> normalizeFields(List<ExtractField> fields) {
        List<ExtractField> result = new ArrayList<>();
        if (fields == null) {
            return result;
        }
        for (ExtractField f : fields) {
            if (f.getName() == null || f.getName().isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "字段名不能为空");
            }
            String type = (f.getDataType() == null || f.getDataType().isBlank())
                    ? "string" : f.getDataType().toLowerCase();
            if (!ALLOWED_TYPES.contains(type)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "字段类型仅支持 string / number / date / boolean / enum");
            }
            ExtractField nf = new ExtractField();
            nf.setKey(f.getKey() == null || f.getKey().isBlank() ? f.getName() : f.getKey().trim());
            nf.setName(f.getName().trim());
            nf.setDataType(type);
            nf.setUnit(f.getUnit());
            nf.setDescription(f.getDescription());
            nf.setAlias(f.getAlias() == null ? null : f.getAlias().trim());
            result.add(nf);
        }
        return result;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

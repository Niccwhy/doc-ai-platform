package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.mapper.ExtractFieldMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 抽取字段定义管理。
 */
@Service
public class ExtractFieldService {

    private static final Set<String> ALLOWED_TYPES = Set.of("string", "number", "date", "boolean");

    private final ExtractFieldMapper fieldMapper;

    public ExtractFieldService(ExtractFieldMapper fieldMapper) {
        this.fieldMapper = fieldMapper;
    }

    public List<ExtractField> list() {
        return fieldMapper.selectList(new QueryWrapper<ExtractField>().orderByAsc("id"));
    }

    public ExtractField get(Long id) {
        ExtractField field = fieldMapper.selectById(id);
        if (field == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字段不存在: " + id);
        }
        return field;
    }

    public ExtractField create(ExtractField field) {
        normalizeAndValidate(field);
        field.setCreatedAt(OffsetDateTime.now());
        fieldMapper.insert(field);
        return field;
    }

    public ExtractField update(Long id, ExtractField patch) {
        ExtractField existing = get(id);
        if (patch.getName() != null) {
            existing.setName(patch.getName());
        }
        if (patch.getAlias() != null) {
            existing.setAlias(patch.getAlias());
        }
        if (patch.getDataType() != null) {
            existing.setDataType(patch.getDataType());
        }
        if (patch.getUnit() != null) {
            existing.setUnit(patch.getUnit());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        normalizeAndValidate(existing);
        fieldMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        get(id);
        fieldMapper.deleteById(id);
    }

    private void normalizeAndValidate(ExtractField field) {
        if (field.getName() == null || field.getName().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "字段名不能为空");
        }
        field.setName(field.getName().trim());
        String type = field.getDataType() == null ? "string" : field.getDataType().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "字段类型仅支持 string / number / date / boolean");
        }
        field.setDataType(type);
        if (field.getAlias() != null) {
            field.setAlias(field.getAlias().trim());
        }
    }
}

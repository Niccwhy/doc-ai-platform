package cn.edu.zjut.backend.dto;

/**
 * Schema 中的单个字段定义（对应 extract_schema.schema_json 的 fields 元素）。
 *
 * @param key         字段标识（英文 key，用于定位与回填映射）
 * @param label       字段中文名（如「甲方」）
 * @param type        类型：string / number / date / boolean / enum
 * @param unit        单位（如「万元」）
 * @param required    是否必填
 * @param description 字段说明，供模型理解
 * @param alias       同义别名（可选，逗号分隔，检索用）
 */
public record SchemaField(
        String key,
        String label,
        String type,
        String unit,
        Boolean required,
        String description,
        String alias
) {
}

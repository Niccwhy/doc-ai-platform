package cn.edu.zjut.backend.entity;

/**
 * 抽取字段定义（纯模型，不再映射表）。
 *
 * <p>字段定义现存放于 {@code extract_schema.schema_json}（JSONB）的 {@code fields} 数组中，
 * 本类是该数组单个元素的 Java 表示，由 {@code ExtractSchemaService} 负责与 JSON 互转。
 */
public class ExtractField {

    /** 字段标识（英文 key，用于定位与回填映射） */
    private String key;

    /** 字段名（如「甲方」） */
    private String name;

    /** 同义别名，逗号分隔 */
    private String alias;

    /** 类型：string / number / date / boolean / enum */
    private String dataType;

    /** 单位（如「万元」） */
    private String unit;

    /** 字段说明，供模型理解 */
    private String description;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

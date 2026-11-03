package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 抽取 Schema 实体，对应表 extract_schema（v3 结构）。
 *
 * <p>字段定义存于 {@code schema_json}（JSONB），形如
 * {@code {"fields":[{"key":"amount","label":"金额","type":"number","unit":"万元","required":true,"description":"合同总金额"}]}}。
 * 序列化/反序列化由 Service 层用 ObjectMapper 处理，本实体以 String 承载。
 */
@TableName("extract_schema")
public class ExtractSchema {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属部门（租户隔离键） */
    private Long deptId;

    /** 创建人 */
    private Long creatorId;

    /** Schema 名称 */
    private String name;

    /** 创建时的自然语言原始描述 */
    private String promptText;

    /** 字段定义 JSON（JSONB） */
    private String schemaJson;

    /** 版本号 */
    private Integer version;

    /** 状态：DRAFT / ACTIVE / ARCHIVED */
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

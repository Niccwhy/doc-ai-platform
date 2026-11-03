package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 抽取结果（含证据链），对应表 extract_result（v3 结构）。
 *
 * <p>对齐 v3 列：task_id / field_key / field_value / value_num / value_date / value_bool
 * / value_unit / raw_value / evidence_block_id / evidence_text / is_selected /
 * is_modified / is_verified / modified_by / modified_at。
 */
@TableName("extract_result")
public class ExtractResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属部门（租户隔离键） */
    private Long deptId;

    /** 归属任务 */
    private Long taskId;

    /** 来源文档 */
    private Long documentId;

    /** 字段标识（对应 schema_json 中的 key） */
    private String fieldKey;

    /** 值类型：TEXT/NUMBER/DATE/BOOLEAN/ENUM */
    private String valueType;

    /** 展示用字段值（含单位） */
    private String fieldValue;

    /** 归一化数值 */
    private BigDecimal valueNum;

    /** 归一化日期 */
    private LocalDate valueDate;

    /** 归一化布尔 */
    private Boolean valueBool;

    /** 值单位 */
    private String valueUnit;

    /** 模型原始输出 */
    private String rawValue;

    /** 置信度 0~1 */
    private Double confidence;

    /** 证据所在块 ID */
    private Long evidenceBlockId;

    /** 证据原文快照 */
    private String evidenceText;

    /** 是否为该字段终稿 */
    private Boolean isSelected;

    /** 是否被人工修改 */
    private Boolean isModified;

    /** 是否人工确认 */
    private Boolean isVerified;

    /** 修改人 */
    private Long modifiedBy;

    /** 修改时间 */
    private OffsetDateTime modifiedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public BigDecimal getValueNum() {
        return valueNum;
    }

    public void setValueNum(BigDecimal valueNum) {
        this.valueNum = valueNum;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public Boolean getValueBool() {
        return valueBool;
    }

    public void setValueBool(Boolean valueBool) {
        this.valueBool = valueBool;
    }

    public String getValueUnit() {
        return valueUnit;
    }

    public void setValueUnit(String valueUnit) {
        this.valueUnit = valueUnit;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Long getEvidenceBlockId() {
        return evidenceBlockId;
    }

    public void setEvidenceBlockId(Long evidenceBlockId) {
        this.evidenceBlockId = evidenceBlockId;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }

    public Boolean getIsSelected() {
        return isSelected;
    }

    public void setIsSelected(Boolean isSelected) {
        this.isSelected = isSelected;
    }

    public Boolean getIsModified() {
        return isModified;
    }

    public void setIsModified(Boolean isModified) {
        this.isModified = isModified;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public Long getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(Long modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public OffsetDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(OffsetDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
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
}

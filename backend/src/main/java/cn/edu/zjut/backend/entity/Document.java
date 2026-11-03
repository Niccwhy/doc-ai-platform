package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 文件元数据实体，对应表 document（v3 结构）。
 * <p>
 * 源文件 / 模板 / 产出统一存本表，用 {@code fileCategory} 区分；
 * 所有查询必须带 {@code deptId}（租户隔离边界）。
 * <p>
 * 注意：不做 (dept_id, sha256) 唯一约束。同内容重复提交是正常业务，
 * 去重通过 {@code findBySha} 给出提示，由用户决定复用还是新建。
 */
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属部门（租户） */
    private Long deptId;
    /** 上传人；系统生成或邮件入库时为空 */
    private Long uploaderId;
    /** USER / SYSTEM / EMAIL */
    private String uploaderType;

    private String fileName;
    /** docx / xlsx / md / txt / pdf，统一小写 */
    private String fileType;
    private String mimeType;
    /** SOURCE / TEMPLATE / OUTPUT */
    private String fileCategory;
    private Long fileSize;
    private String sha256;
    private String storageBucket;
    private String storageKey;

    /** WEB / EMAIL / API */
    private String sourceType;
    /** 邮件 message-id 等外部溯源标识 */
    private String sourceRef;
    /** 是否全部门可见的共有资产（规范手册等） */
    private Boolean isShared;

    /** UPLOADED / PARSING / PARSED / FAILED / SKIPPED */
    private String parseStatus;
    private String parseError;
    private Integer parseAttempts;
    private OffsetDateTime parsedAt;

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

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderType() {
        return uploaderType;
    }

    public void setUploaderType(String uploaderType) {
        this.uploaderType = uploaderType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFileCategory() {
        return fileCategory;
    }

    public void setFileCategory(String fileCategory) {
        this.fileCategory = fileCategory;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public Boolean getIsShared() {
        return isShared;
    }

    public void setIsShared(Boolean isShared) {
        this.isShared = isShared;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }

    public Integer getParseAttempts() {
        return parseAttempts;
    }

    public void setParseAttempts(Integer parseAttempts) {
        this.parseAttempts = parseAttempts;
    }

    public OffsetDateTime getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(OffsetDateTime parsedAt) {
        this.parsedAt = parsedAt;
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

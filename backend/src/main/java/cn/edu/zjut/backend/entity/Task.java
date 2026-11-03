package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 异步任务表实体，对应表 task（v3 结构）。
 *
 * <p>任务号即主键 {@code id}（BIGINT 自增），不再使用 v1 的字符串 task_id。
 * 状态机：PENDING / ASSIGNED / PROCESSING / COMPLETED / FAILED / CANCELLED。
 *
 * <p>JSON 列（payload_json / result_json / schema_snapshot）用 String 承载，
 * 由 Service 层序列化，避免 MyBatis-Plus 对 JSONB 的类型映射复杂度。
 */
@TableName("task")
public class Task {

    /** 任务状态常量（对齐 v3 的 ck_task_status） */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 任务类型常量（对齐 v3 的 ck_task_type） */
    public static final String TYPE_EXTRACT = "EXTRACT";
    public static final String TYPE_FILL = "FILL";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属部门（租户隔离键） */
    private Long deptId;

    /** 创建人 */
    private Long creatorId;

    /** 处理人 */
    private Long assigneeId;

    /** 任务类型：EXTRACT / FILL */
    private String taskType;

    /** 关联的抽取 Schema */
    private Long schemaId;

    /** 填表使用的模板文件 ID */
    private Long templateDocId;

    /** 建单时的 Schema 快照（JSON 字符串） */
    private String schemaSnapshot;

    /** 状态：PENDING/ASSIGNED/PROCESSING/COMPLETED/FAILED/CANCELLED */
    private String status;

    /** 进度百分比 0-100 */
    private Integer progress;

    /** 优先级，1 最高、9 最低 */
    private Integer priority;

    /** 任务入参快照（JSON 字符串） */
    private String payloadJson;

    /** 执行结果摘要（JSON 字符串） */
    private String resultJson;

    /** 产出的文件 ID */
    private Long resultDocId;

    /** 失败原因 */
    private String errorMsg;

    /** 已尝试次数 */
    private Integer attemptCount;

    /** 认领时间 */
    private OffsetDateTime claimedAt;

    /** 租约到期时间 */
    private OffsetDateTime leaseExpiresAt;

    /** 开始执行时间 */
    private OffsetDateTime startedAt;

    /** 结束时间（完成/失败/取消） */
    private OffsetDateTime completedAt;

    /** 乐观锁版本号 */
    private Integer versionNo;

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

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(Long schemaId) {
        this.schemaId = schemaId;
    }

    public Long getTemplateDocId() {
        return templateDocId;
    }

    public void setTemplateDocId(Long templateDocId) {
        this.templateDocId = templateDocId;
    }

    public String getSchemaSnapshot() {
        return schemaSnapshot;
    }

    public void setSchemaSnapshot(String schemaSnapshot) {
        this.schemaSnapshot = schemaSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Long getResultDocId() {
        return resultDocId;
    }

    public void setResultDocId(Long resultDocId) {
        this.resultDocId = resultDocId;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
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

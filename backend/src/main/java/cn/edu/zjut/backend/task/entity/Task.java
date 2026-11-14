package cn.edu.zjut.backend.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long creatorId;
    private Long assigneeId;
    private String taskType;
    private Long schemaId;
    private Long templateDocId;
    private String status;
    private Long resultDocId;
    private Long resultEditableDocId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

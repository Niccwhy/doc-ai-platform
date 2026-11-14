package cn.edu.zjut.backend.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_log")
public class TaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long operatorId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}

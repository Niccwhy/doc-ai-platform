package cn.edu.zjut.backend.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("task_document_rel")
public class TaskDocumentRel {

    private Long taskId;
    private Long documentId;
    private Long documentVersionId;
}

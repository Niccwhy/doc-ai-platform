package cn.edu.zjut.backend.email.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_pending")
public class EmailPending {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long emailUid;
    private String sender;
    private String subject;
    private String attachmentName;
    private Long attachmentSize;
    private String tempStorageKey;
    private Long suggestedDocumentId;
    private Long suggestedPendingId;
    private String status;
    private LocalDateTime createdAt;
}

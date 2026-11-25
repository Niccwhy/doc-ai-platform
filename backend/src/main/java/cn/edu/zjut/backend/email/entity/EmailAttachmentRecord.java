package cn.edu.zjut.backend.email.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_attachment")
public class EmailAttachmentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long emailId;
    private String partId;
    private String attachmentName;
    private String contentType;
    private Long attachmentSize;
    private String status;
    private Long documentVersionId;
    private LocalDateTime createdAt;
}
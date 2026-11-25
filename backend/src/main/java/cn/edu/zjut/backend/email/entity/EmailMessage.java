package cn.edu.zjut.backend.email.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_message")
public class EmailMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long emailConfigId;
    private Long uidValidity;
    private Long emailUid;
    private String sender;
    private String subject;
    private LocalDateTime receivedAt;
    private String bodyText;
    private String status;
    private LocalDateTime createdAt;
}
package cn.edu.zjut.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dept_email_config")
public class DeptEmailConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String emailAddress;
    private String imapHost;
    private Integer imapPort;
    private String authCode;
    private Long lastUid;
    private Integer status;
    private LocalDateTime createdAt;
}

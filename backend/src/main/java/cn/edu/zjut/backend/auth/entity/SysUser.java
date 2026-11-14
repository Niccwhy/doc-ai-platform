package cn.edu.zjut.backend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String username;
    private String passwordHash;
    private String realName;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

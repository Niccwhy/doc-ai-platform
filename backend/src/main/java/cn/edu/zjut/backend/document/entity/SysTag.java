package cn.edu.zjut.backend.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tag")
public class SysTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String tagName;
    private LocalDateTime createdAt;
}

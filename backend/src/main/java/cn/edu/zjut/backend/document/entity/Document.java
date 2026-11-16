package cn.edu.zjut.backend.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long uploaderId;
    private String fileName;
    private String fileCategory;
    private String sourceType;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

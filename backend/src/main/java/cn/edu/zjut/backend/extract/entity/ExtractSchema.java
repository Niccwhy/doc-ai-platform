package cn.edu.zjut.backend.extract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("extract_schema")
public class ExtractSchema {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long creatorId;
    private String name;
    private String promptText;
    private String schemaJson;
    private LocalDateTime createdAt;
}

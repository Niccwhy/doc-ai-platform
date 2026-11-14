package cn.edu.zjut.backend.editable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("editable_doc")
public class EditableDoc {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long creatorId;
    private String title;
    private Long sourceTaskId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}

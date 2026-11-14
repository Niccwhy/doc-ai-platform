package cn.edu.zjut.backend.editable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("editable_doc_version")
public class EditableDocVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long editableDocId;
    private Integer versionNo;
    private String contentJson;
    private String operationType;
    private Long createdBy;
    private LocalDateTime createdAt;
}

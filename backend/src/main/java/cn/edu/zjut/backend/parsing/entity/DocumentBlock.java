package cn.edu.zjut.backend.parsing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_block")
public class DocumentBlock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentVersionId;
    private Integer blockIndex;
    private String blockType;
    private String content;
    private String tableJson;
    private String embedding;
    private LocalDateTime createdAt;
}

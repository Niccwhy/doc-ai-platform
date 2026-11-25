package cn.edu.zjut.backend.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("document_tag_rel")
public class DocumentTagRel {

    private Long documentId;
    private Long tagId;
}

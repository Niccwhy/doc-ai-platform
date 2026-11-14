package cn.edu.zjut.backend.extract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("extract_result")
public class ExtractResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long documentId;
    private String fieldKey;
    private String fieldValue;
    private BigDecimal confidence;
    private Long evidenceBlockId;
    private Integer isModified;
    private LocalDateTime createdAt;
}

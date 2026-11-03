package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 文档片段表实体，对应表 doc_chunk。
 * <p>
 * 注意：embedding 字段为 pgvector 类型，MyBatis-Plus 默认映射复杂，
 * 这里不声明该字段，向量读写走 JdbcTemplate（见 ChunkVectorDao）。
 */
@TableName("doc_chunk")
public class DocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer seq;
    private String chunkType;   // text / table
    private String headingPath;
    private String content;
    private String cellRef;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public String getHeadingPath() {
        return headingPath;
    }

    public void setHeadingPath(String headingPath) {
        this.headingPath = headingPath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCellRef() {
        return cellRef;
    }

    public void setCellRef(String cellRef) {
        this.cellRef = cellRef;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

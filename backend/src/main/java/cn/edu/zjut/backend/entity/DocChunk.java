package cn.edu.zjut.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 文档片段表实体，对应表 document_block（v3 结构，原 doc_chunk）。
 * <p>
 * 注意：embedding 字段为 pgvector 类型，MyBatis-Plus 默认映射复杂，
 * 这里不声明该字段，向量读写走 JdbcTemplate（见 ChunkVectorDao）。
 */
@TableName("document_block")
public class DocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 冗余租户列（向量检索必须先按它过滤） */
    private Long deptId;
    private Long documentId;
    /** 块在文档内的序号 */
    private Integer blockIndex;
    /** 块类型：TITLE / PARAGRAPH / TABLE / IMAGE */
    private String blockType;
    private String headingPath;
    private String content;
    private String cellRef;
    private Integer pageNo;
    private Integer charLen;
    private Integer tokenCount;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(Integer blockIndex) {
        this.blockIndex = blockIndex;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
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

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getCharLen() {
        return charLen;
    }

    public void setCharLen(Integer charLen) {
        this.charLen = charLen;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

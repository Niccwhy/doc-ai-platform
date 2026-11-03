package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.dao.ChunkVectorDao;
import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.entity.DocChunk;
import cn.edu.zjut.backend.entity.Document;
import cn.edu.zjut.backend.mapper.DocChunkMapper;
import cn.edu.zjut.backend.mapper.DocumentMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import cn.edu.zjut.backend.service.embedding.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 建库索引服务：把解析结果切片 → 向量化 → 写入 document_block（含 embedding）。
 * 供「批量上传」与「单文档入库」复用，是跨文档检索的数据基础。
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final ChunkVectorDao chunkVectorDao;
    private final EmbeddingClient embeddingClient;

    public IndexingService(DocumentMapper documentMapper,
                           DocChunkMapper docChunkMapper,
                           ChunkVectorDao chunkVectorDao,
                           EmbeddingClient embeddingClient) {
        this.documentMapper = documentMapper;
        this.docChunkMapper = docChunkMapper;
        this.chunkVectorDao = chunkVectorDao;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 建库：创建 document 记录，解析结果切片入库并向量化。
     *
     * @return 入库的 documentId
     */
    @Transactional
    public Long index(String fileName, String fileType, String objectKey,
                      long sizeBytes, DocParseResult parseResult) {
        Long deptId = CurrentUser.deptId();

        // 1. 写 document
        Document doc = new Document();
        doc.setDeptId(deptId);
        doc.setUploaderId(CurrentUser.id());
        doc.setUploaderType("USER");
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setStorageKey(objectKey);
        doc.setStorageBucket("docai");
        doc.setFileSize(sizeBytes);
        doc.setFileCategory("SOURCE");
        doc.setSourceType("WEB");
        doc.setParseStatus("PARSING");
        doc.setParseAttempts(0);
        doc.setCreatedAt(OffsetDateTime.now());
        documentMapper.insert(doc);
        Long documentId = doc.getId();

        // 2. 切片入库
        List<DocChunk> chunks = toChunks(deptId, documentId, parseResult);
        for (DocChunk chunk : chunks) {
            docChunkMapper.insert(chunk);
        }

        // 3. 向量化（逐条 embed，避免批量超限）
        if (embeddingClient.isAvailable()) {
            for (DocChunk chunk : chunks) {
                try {
                    float[] vec = embeddingClient.embed(chunk.getContent());
                    if (vec != null && vec.length > 0) {
                        chunkVectorDao.updateEmbedding(chunk.getId(), vec);
                    }
                } catch (Exception e) {
                    log.warn("chunk {} 向量化失败: {}", chunk.getId(), e.getMessage());
                }
            }
        } else {
            log.warn("Embedding 未配置 key，跳过向量化（仅关键词召回可用）");
        }

        // 4. 更新状态
        doc.setParseStatus("PARSED");
        doc.setParsedAt(OffsetDateTime.now());
        documentMapper.updateById(doc);
        log.info("文档 {} 入库完成：{} 个片段", fileName, chunks.size());
        return documentId;
    }

    private List<DocChunk> toChunks(Long deptId, Long documentId, DocParseResult result) {
        List<DocChunk> list = new ArrayList<>();
        int seq = 0;
        for (DocParseResult.Chunk c : result.chunks()) {
            DocChunk chunk = new DocChunk();
            chunk.setDeptId(deptId);
            chunk.setDocumentId(documentId);
            chunk.setBlockIndex(seq++);
            chunk.setBlockType(c.type());
            chunk.setHeadingPath(c.headingPath());
            chunk.setContent(c.content());
            chunk.setCellRef(c.cellRef());
            chunk.setCharLen(c.content() == null ? 0 : c.content().length());
            chunk.setCreatedAt(OffsetDateTime.now());
            list.add(chunk);
        }
        return list;
    }
}

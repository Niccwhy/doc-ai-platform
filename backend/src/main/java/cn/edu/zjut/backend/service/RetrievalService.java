package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.dao.ChunkVectorDao;
import cn.edu.zjut.backend.entity.DocChunk;
import cn.edu.zjut.backend.mapper.DocChunkMapper;
import cn.edu.zjut.backend.service.embedding.EmbeddingClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨文档双路召回检索：向量相似度 + 关键词（全文）召回，合并去重后返回上下文片段。
 * 供信息抽取与模板填表复用。
 */
@Service
public class RetrievalService {

    private final DocChunkMapper docChunkMapper;
    private final ChunkVectorDao chunkVectorDao;
    private final EmbeddingClient embeddingClient;

    public RetrievalService(DocChunkMapper docChunkMapper,
                            ChunkVectorDao chunkVectorDao,
                            EmbeddingClient embeddingClient) {
        this.docChunkMapper = docChunkMapper;
        this.chunkVectorDao = chunkVectorDao;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 双路召回 Top-K 片段。
     *
     * @param query    查询词（字段名 / 表头 / 短语）
     * @param topK     返回条数
     * @return 按相关度降序的上下文片段文本
     */
    public List<String> retrieve(String query, int topK) {
        Map<Long, DocChunk> merged = new LinkedHashMap<>();

        // 路 1：向量召回（有 embedding 才走）
        if (embeddingClient.isAvailable() && chunkVectorDao.countWithEmbedding() > 0) {
            try {
                float[] qv = embeddingClient.embed(query);
                List<Map<String, Object>> hits = chunkVectorDao.vectorSearch(qv, topK, 0.3);
                for (Map<String, Object> hit : hits) {
                    long id = ((Number) hit.get("id")).longValue();
                    DocChunk chunk = docChunkMapper.selectById(id);
                    if (chunk != null) {
                        merged.put(id, chunk);
                    }
                }
            } catch (Exception e) {
                // 向量召回失败不阻塞，降级关键词
            }
        }

        // 路 2：关键词（全文）召回
        List<DocChunk> kwHits = docChunkMapper.selectList(new QueryWrapper<DocChunk>()
                .like("content", query)
                .last("LIMIT " + topK));
        for (DocChunk chunk : kwHits) {
            merged.putIfAbsent(chunk.getId(), chunk);
        }

        // 输出文本
        List<String> result = new ArrayList<>();
        for (DocChunk chunk : merged.values()) {
            if (result.size() >= topK) {
                break;
            }
            result.add(chunk.getContent());
        }
        return result;
    }
}

package cn.edu.zjut.backend.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * pgvector 向量读写 DAO。
 * <p>
 * 用纯字符串方式构造 vector 字面量（"[0.1,0.2,...]"），配合 CAST(? AS vector)
 * 交给 PostgreSQL 解析，不依赖驱动层的 PGobject 类，规避 runtime scope 问题。
 */
@Repository
public class ChunkVectorDao {

    private final JdbcTemplate jdbcTemplate;

    public ChunkVectorDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 更新指定 chunk 的向量。
     */
    public void updateEmbedding(long chunkId, float[] vector) {
        String sql = "UPDATE doc_chunk SET embedding = CAST(? AS vector) WHERE id = ?";
        jdbcTemplate.update(sql, toLiteral(vector), chunkId);
    }

    /**
     * 向量余弦相似度 Top-K 召回。
     *
     * @param queryVector 查询向量
     * @param topK        返回条数
     * @param minScore    最低相似度阈值（0~1），低于则过滤
     * @return 按相似度降序的 id -> score 列表
     */
    public List<Map<String, Object>> vectorSearch(float[] queryVector, int topK, double minScore) {
        String literal = toLiteral(queryVector);
        String sql = "SELECT id, 1 - (embedding <=> CAST(? AS vector)) AS score FROM doc_chunk " +
                "WHERE embedding IS NOT NULL " +
                "AND 1 - (embedding <=> CAST(? AS vector)) >= ? " +
                "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?";
        return jdbcTemplate.queryForList(sql, literal, literal, minScore, literal, topK);
    }

    /**
     * 向量字段是否已写入（用于判断是否走向量召回）。
     */
    public long countWithEmbedding() {
        Long n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM doc_chunk WHERE embedding IS NOT NULL", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * float[] -> pgvector 字面量字符串，如 "[0.1,0.2,0.3]"。
     */
    private String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}

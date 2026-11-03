package cn.edu.zjut.backend.service.embedding;

import java.util.List;

/**
 * Embedding 向量化客户端抽象。把文本转成定长向量，用于 pgvector 检索。
 * <p>
 * 实现：
 * <ul>
 *   <li>{@link OpenAICompatEmbeddingClient}：OpenAI 兼容接口（通义 DashScope 等）</li>
 *   <li>{@link KeywordEmbeddingClient}：无 key 时的关键词降级（返回稀疏向量）</li>
 * </ul>
 */
public interface EmbeddingClient {

    /**
     * 批量向量化。
     *
     * @param texts 文本列表（可单条）
     * @return 与 texts 顺序一致的向量列表，每个向量维度 = {@link #dimension()}
     */
    List<float[]> embed(List<String> texts);

    /**
     * 单条向量化。
     */
    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * 向量维度。
     */
    int dimension();

    /**
     * 是否有真实 key（可调用远端服务）。
     */
    boolean isAvailable();
}

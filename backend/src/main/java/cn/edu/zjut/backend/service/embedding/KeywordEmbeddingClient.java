package cn.edu.zjut.backend.service.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词降级 Embedding 客户端：无 api-key 时使用。
 * <p>
 * 生成一个"伪向量"：按 UTF-8 字节做简单散列映射到指定维度。不能做语义相似度，
 * 仅保证「相同/相似文本落在相近向量」，供无 key 联调时链路不中断。
 * 真实语义检索需配置通义 key。
 */
public class KeywordEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public KeywordEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(hashVector(text == null ? "" : text));
        }
        return result;
    }

    private float[] hashVector(String text) {
        float[] vec = new float[dimension];
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int idx = Math.floorMod(hash(bytes[i], i), dimension);
            vec[idx] += 1.0f;
        }
        // 归一化
        double norm = 0.0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        }
        return vec;
    }

    private int hash(byte b, int i) {
        return (b * 31 + i * 17 + 7) & 0x7fffffff;
    }
}

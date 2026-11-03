package cn.edu.zjut.backend.config;

import cn.edu.zjut.backend.service.llm.DeepSeekClient;
import cn.edu.zjut.backend.service.llm.LLMClient;
import cn.edu.zjut.backend.service.llm.MockLLMClient;
import cn.edu.zjut.backend.service.embedding.EmbeddingClient;
import cn.edu.zjut.backend.service.embedding.OpenAICompatEmbeddingClient;
import cn.edu.zjut.backend.service.embedding.KeywordEmbeddingClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 网关 + Embedding 配置。
 * <p>
 * 密钥来源优先级：环境变量（.env 注入）→ application-local.yaml 覆盖 → 缺省空串（降级）。
 * <ul>
 *   <li>llm.api-key 为空 → 降级 {@link MockLLMClient}（返回 null）</li>
 *   <li>embedding.api-key 为空 → 降级 {@link KeywordEmbeddingClient}（关键词召回）</li>
 * </ul>
 */
@Configuration
public class LLMConfig {

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LLMProperties llmProperties() {
        return new LLMProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "embedding")
    public EmbeddingProperties embeddingProperties() {
        return new EmbeddingProperties();
    }

    /**
     * 对话 LLM 客户端：有 key 用 DeepSeek，无 key 用 Mock。
     */
    @Bean
    public LLMClient llmClient(LLMProperties props) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new DeepSeekClient(props.getApiKey(), props.getBaseUrl(), props.getModel());
        }
        return new MockLLMClient();
    }

    /**
     * Embedding 客户端：有 key 用 OpenAI 兼容（通义 DashScope），无 key 用关键词降级。
     */
    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties props) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAICompatEmbeddingClient(
                    props.getApiKey(), props.getBaseUrl(), props.getModel(), props.getDimension());
        }
        return new KeywordEmbeddingClient(props.getDimension());
    }

    public static class LLMProperties {
        private String provider = "deepseek";
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-flash";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class EmbeddingProperties {
        private String provider = "qwen";
        private String apiKey = "";
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "text-embedding-v3";
        private int dimension = 1536;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }
}

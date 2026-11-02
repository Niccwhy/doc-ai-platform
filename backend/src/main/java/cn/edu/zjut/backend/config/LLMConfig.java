package cn.edu.zjut.backend.config;

import cn.edu.zjut.backend.service.llm.DeepSeekClient;
import cn.edu.zjut.backend.service.llm.LLMClient;
import cn.edu.zjut.backend.service.llm.MockLLMClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 网关配置：有 api-key 用真实 client，无则降级 Mock。
 */
@Configuration
public class LLMConfig {

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LLMProperties llmProperties() {
        return new LLMProperties();
    }

    @Bean
    public LLMClient llmClient(LLMProperties props) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new DeepSeekClient(props.getApiKey(), props.getBaseUrl(), props.getModel());
        }
        return new MockLLMClient();
    }

    public static class LLMProperties {
        private String provider = "deepseek";
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private String embeddingModel = "text-embedding-v1";

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

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }
}

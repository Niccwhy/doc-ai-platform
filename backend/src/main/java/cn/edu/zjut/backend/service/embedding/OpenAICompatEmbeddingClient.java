package cn.edu.zjut.backend.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 客户端（通义 DashScope / 其他兼容服务）。
 * 调用 POST {baseUrl}/embeddings，输入文本列表，返回向量列表。
 */
public class OpenAICompatEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatEmbeddingClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimension;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAICompatEmbeddingClient(String apiKey, String baseUrl, String model, int dimension) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimension = dimension;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", texts,
                    "encoding_format", "float"
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Embedding 调用失败，HTTP {}: {}", response.statusCode(), response.body());
                return emptyVectors(texts.size());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            List<float[]> vectors = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vec);
            }
            return vectors;
        } catch (Exception e) {
            log.error("Embedding 调用异常", e);
            return emptyVectors(texts.size());
        }
    }

    private List<float[]> emptyVectors(int size) {
        List<float[]> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new float[0]);
        }
        return result;
    }
}

package cn.edu.zjut.backend.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 客户端。通过 OpenAI 兼容的 chat/completions 接口做结构化抽取。
 * 无额外依赖，使用 JDK 内置 HttpClient。
 */
public class DeepSeekClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public DeepSeekClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Map<String, String> extract(String context, List<String> fields, String instruction) {
        String system = buildSystemPrompt(fields, instruction);
        String user = "请从以下原文中抽取字段值：\n\n" + context;

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object")
            );

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("LLM 调用失败，HTTP {}: {}", response.statusCode(), response.body());
                return Map.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return parseJsonObject(content, fields);

        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return Map.of();
        }
    }

    private String buildSystemPrompt(List<String> fields, String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个信息抽取助手。请从给定的原文中抽取以下字段的值，只输出 JSON 对象。\n");
        sb.append("字段列表：").append(String.join("、", fields)).append("\n");
        sb.append("规则：\n");
        sb.append("1. 输出格式：{\"字段名\": \"值\", ...}，所有字段都必须出现。\n");
        sb.append("2. 如果原文中找不到某个字段的值，该字段值设为 null，绝不编造。\n");
        sb.append("3. 数值字段只保留数值本身，去掉单位（单位单独说明时保留原样）。\n");
        if (instruction != null && !instruction.isBlank()) {
            sb.append("额外要求：").append(instruction).append("\n");
        }
        return sb.toString();
    }

    private Map<String, String> parseJsonObject(String content, List<String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            JsonNode node = objectMapper.readTree(content);
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value == null || value.isNull()) {
                    result.put(field, null);
                } else {
                    result.put(field, value.asText());
                }
            }
        } catch (Exception e) {
            log.warn("LLM 返回 JSON 解析失败: {}", content);
            for (String field : fields) {
                result.put(field, null);
            }
        }
        return result;
    }
}

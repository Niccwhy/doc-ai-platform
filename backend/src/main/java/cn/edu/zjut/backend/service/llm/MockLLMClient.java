package cn.edu.zjut.backend.service.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock LLM 客户端：无 API Key 时用于联调，返回字段名占位值。
 */
public class MockLLMClient implements LLMClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Map<String, String> extract(String context, List<String> fields, String instruction) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : fields) {
            result.put(field, null); // mock 返回 null，表示"未命中"
        }
        return result;
    }
}

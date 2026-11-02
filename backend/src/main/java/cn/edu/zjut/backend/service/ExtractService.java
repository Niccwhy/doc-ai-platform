package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.service.llm.LLMClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息抽取服务：串起「检索 + LLM 结构化抽取」。
 * 当前为框架版：检索用简单关键词匹配占位，待向量检索（方向 8 后续）接入。
 */
@Service
public class ExtractService {

    private final LLMClient llmClient;

    public ExtractService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 从给定上下文中抽取字段值。
     */
    public Map<String, String> extract(String context, List<String> fields, String instruction) {
        return llmClient.extract(context, fields, instruction);
    }

    /**
     * LLM 网关状态（用于探活）。
     */
    public Map<String, Object> llmStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", llmClient.isAvailable());
        status.put("client", llmClient.getClass().getSimpleName());
        return status;
    }
}

package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.service.llm.LLMClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息抽取服务：串起「检索 + LLM 结构化抽取」。
 * <p>
 * 真实链路：字段名 → 跨文档召回上下文 → LLM 抽取字段值。
 * 无 LLM key 时 LLM 返回 null（Mock），链路仍可走通便于联调。
 */
@Service
public class ExtractService {

    private final LLMClient llmClient;
    private final RetrievalService retrievalService;

    public ExtractService(LLMClient llmClient, RetrievalService retrievalService) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
    }

    /**
     * 从样本集检索并抽取字段值（走检索链路）。
     *
     * @param fields      待抽取字段名列表
     * @param instruction 额外抽取指令（可选）
     */
    public Map<String, String> extractFromCorpus(List<String> fields, String instruction) {
        // 用字段名做检索 query，召回上下文后交给 LLM
        String query = String.join(" ", fields);
        List<String> contexts = retrievalService.retrieve(query, 5);
        String context = String.join("\n\n", contexts);
        return llmClient.extract(context, fields, instruction);
    }

    /**
     * 从给定上下文直接抽取（保留原接口，便于单测与逐段抽取）。
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

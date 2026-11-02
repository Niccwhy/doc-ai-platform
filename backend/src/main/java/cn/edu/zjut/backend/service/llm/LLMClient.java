package cn.edu.zjut.backend.service.llm;

import java.util.Map;

/**
 * LLM 客户端抽象。负责调用大模型做结构化信息抽取。
 * 实现：DeepSeekClient（真实）、MockLLMClient（无 Key 联调用）。
 */
public interface LLMClient {

    /**
     * 结构化抽取：给定上下文片段与字段定义，返回字段名 -> 取值 的映射。
     * 约定：找不到的值返回 null，不编造。
     *
     * @param context  检索到的原文证据片段
     * @param fields   待抽取字段名列表
     * @param instruction 额外的抽取指令（可选）
     */
    Map<String, String> extract(String context, java.util.List<String> fields, String instruction);

    /**
     * 是否可用（有真实 Key）。
     */
    boolean isAvailable();
}

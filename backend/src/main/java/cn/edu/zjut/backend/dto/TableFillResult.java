package cn.edu.zjut.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 模板填表结果：按字段回填后的映射，以及逐字段的填充明细（值、来源、置信度）。
 */
public record TableFillResult(
        String fileName,
        int fieldCount,
        List<FieldFill> fields
) {
    /**
     * 单个字段的填充明细。
     */
    public record FieldFill(
            String fieldName,     // 字段名（表头）
            String value,         // 填写的值
            String source,        // 来源（retrieved / llm / rule / null）
            Double confidence,    // 置信度 0~1（无则为 null）
            String evidence        // 证据原文（可溯源）
    ) {}
}

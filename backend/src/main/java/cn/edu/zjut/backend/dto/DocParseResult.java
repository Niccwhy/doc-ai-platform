package cn.edu.zjut.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 文档解析后的统一结构：一份文档 -> 多个片段（chunk）。
 */
public record DocParseResult(
        String fileName,
        String fileType,
        int chunkCount,
        List<Chunk> chunks
) {

    /**
     * 单个片段：文本或表格，含标题路径、原文、可选的单元格坐标与向量占位。
     */
    public record Chunk(
            int seq,
            String type,          // text / table
            String headingPath,   // 标题层级路径，如 "1. 概述 > 1.1 背景"
            String content,       // 文本内容 或 表格的序列化内容
            String cellRef,       // 表格片段：sheet 名 + 单元格坐标，如 "Sheet1!A1:D10"
            List<List<String>> tableData, // 表格片段：二维数据
            Map<String, String> meta   // 附加元信息（数字/日期类型标注等）
    ) {}
}

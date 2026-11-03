package cn.edu.zjut.backend.dto;

import java.util.List;

/**
 * 模板回填结果：回填后的文件字节 + 写入位置明细 + 未匹配字段。
 */
public record FillOutcome(
        byte[] content,
        String fileName,
        String format,              // xlsx / docx
        int filledCount,
        List<WriteRecord> records,
        List<String> unmatchedFields
) {

    /**
     * 单次写入记录，用于核对与评测（字段 → 目标坐标）。
     */
    public record WriteRecord(
            String field,
            String targetRef,   // 如 "Sheet1!B2" 或 "table1[r2,c1]"
            String value
    ) {}
}

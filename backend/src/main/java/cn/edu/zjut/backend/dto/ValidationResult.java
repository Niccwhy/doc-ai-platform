package cn.edu.zjut.backend.dto;

/**
 * 抽取值的校验结果。
 *
 * @param accepted        是否通过校验（可入库/可采信）
 * @param confidence      置信度 0~1
 * @param normalizedValue 归一化后的值
 * @param reason          判定说明（未通过时给出原因）
 */
public record ValidationResult(
        boolean accepted,
        double confidence,
        String normalizedValue,
        String reason
) {
}

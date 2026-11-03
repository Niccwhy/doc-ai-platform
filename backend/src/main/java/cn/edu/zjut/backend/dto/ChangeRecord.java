package cn.edu.zjut.backend.dto;

/**
 * 单条指令的执行效果，用于预览与审计。
 *
 * @param scope    作用范围
 * @param action   动作
 * @param detail   可读说明（如「4 个段落」「替换 3 处」）
 * @param affected 实际影响数量
 */
public record ChangeRecord(
        String scope,
        String action,
        String detail,
        int affected
) {
}

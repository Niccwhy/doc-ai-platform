package cn.edu.zjut.backend.dto;

/**
 * 文档版本信息。
 *
 * @param version   版本号（从 1 递增）
 * @param note      版本说明（如「全文加粗」「rollback from v2」）
 * @param size      文件字节数
 * @param createdAt 创建时间（ISO 8601）
 */
public record VersionInfo(
        int version,
        String note,
        long size,
        String createdAt
) {
}

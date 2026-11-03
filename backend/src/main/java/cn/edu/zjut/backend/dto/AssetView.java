package cn.edu.zjut.backend.dto;

import cn.edu.zjut.backend.entity.Document;

import java.time.OffsetDateTime;

/**
 * 文件池列表项（对外视图，屏蔽 storageKey 等内部字段）。
 */
public record AssetView(
        Long id,
        String fileName,
        String fileType,
        String fileCategory,
        Long fileSize,
        String parseStatus,
        String sourceType,
        String uploaderType,
        Boolean shared,
        OffsetDateTime createdAt
) {

    public static AssetView from(Document doc) {
        return new AssetView(
                doc.getId(),
                doc.getFileName(),
                doc.getFileType(),
                doc.getFileCategory(),
                doc.getFileSize(),
                doc.getParseStatus(),
                doc.getSourceType(),
                doc.getUploaderType(),
                doc.getIsShared(),
                doc.getCreatedAt());
    }
}

package cn.edu.zjut.backend.dto;

/**
 * 单个文件的上传结果。
 *
 * @param documentId 文档 id（复用已有文件时为已有记录的 id）
 * @param fileName   原始文件名
 * @param duplicate  是否为内容重复（true 表示未新建记录，返回的是库中已有文件）
 * @param storageKey 对象存储 key（内容寻址，同内容共享同一 key）
 * @param reusedBlob 对象是否已存在而被复用（省一次上传）
 */
public record AssetUploadResult(
        Long documentId,
        String fileName,
        boolean duplicate,
        String storageKey,
        boolean reusedBlob
) {
}

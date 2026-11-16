package cn.edu.zjut.backend.document.dto;

public record UploadDocumentResponse(
        Long documentId,
        Long versionId,
        String fileName,
        String fileCategory,
        Long fileSize,
        String sha256,
        Integer versionNo,
        boolean duplicate) {
}

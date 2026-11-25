package cn.edu.zjut.backend.email.dto;

public record EmailAttachmentResponse(
        Long id,
        String partId,
        String fileName,
        String contentType,
        Long size,
        String status,
        Long documentVersionId) {
}
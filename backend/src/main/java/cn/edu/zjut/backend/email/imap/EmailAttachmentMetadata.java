package cn.edu.zjut.backend.email.imap;

public record EmailAttachmentMetadata(
        String partId,
        String fileName,
        String contentType,
        Long size) {
}
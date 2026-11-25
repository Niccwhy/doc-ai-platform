package cn.edu.zjut.backend.email.imap;

public record EmailAttachmentContent(
        String fileName,
        String contentType,
        byte[] content) {
}
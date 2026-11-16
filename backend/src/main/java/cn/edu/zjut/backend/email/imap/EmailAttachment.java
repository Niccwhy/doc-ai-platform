package cn.edu.zjut.backend.email.imap;

public record EmailAttachment(
        long emailUid,
        String sender,
        String subject,
        String fileName,
        String contentType,
        byte[] content) {
}

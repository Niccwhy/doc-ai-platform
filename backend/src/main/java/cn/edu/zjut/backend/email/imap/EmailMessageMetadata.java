package cn.edu.zjut.backend.email.imap;

import java.time.LocalDateTime;
import java.util.List;

public record EmailMessageMetadata(
        long emailUid,
        String sender,
        String subject,
        LocalDateTime receivedAt,
        String bodyText,
        List<EmailAttachmentMetadata> attachments) {
}
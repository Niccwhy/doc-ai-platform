package cn.edu.zjut.backend.email.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EmailMessageResponse(
        Long id,
        Long emailUid,
        String sender,
        String subject,
        LocalDateTime receivedAt,
        String bodyText,
        String status,
        List<EmailAttachmentResponse> attachments) {
}
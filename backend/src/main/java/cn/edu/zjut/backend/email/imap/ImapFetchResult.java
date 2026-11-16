package cn.edu.zjut.backend.email.imap;

import java.util.List;

public record ImapFetchResult(long maxUid, List<EmailAttachment> attachments) {
}

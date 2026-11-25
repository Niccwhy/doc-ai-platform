package cn.edu.zjut.backend.email.imap;

import java.util.List;

public record ImapDiscoveryResult(long uidValidity, long maxUid, List<EmailMessageMetadata> messages) {
}
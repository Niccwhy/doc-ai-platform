package cn.edu.zjut.backend.email.imap;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.exception.BizException;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Component
public class JakartaImapMailReader implements ImapMailReader {

    private static final int DEFAULT_IMAP_PORT = 993;
    private static final int CONNECTION_TIMEOUT_MS = 10_000;
    private static final String INBOX = "INBOX";

    @Override
    public ImapFetchResult fetch(DeptEmailConfig config, long lastUid) {
        int port = config.getImapPort() == null ? DEFAULT_IMAP_PORT : config.getImapPort();
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", config.getImapHost());
        properties.put("mail.imaps.port", String.valueOf(port));
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.connectiontimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        properties.put("mail.imaps.timeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        Session session = Session.getInstance(properties);

        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore("imaps");
            store.connect(config.getImapHost(), port, config.getEmailAddress(), config.getAuthCode());
            folder = store.getFolder(INBOX);
            folder.open(Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;
            Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
            if (messages == null) {
                return new ImapFetchResult(lastUid, List.of());
            }

            long maxUid = lastUid;
            List<EmailAttachment> attachments = new ArrayList<>();
            for (Message message : messages) {
                long emailUid = uidFolder.getUID(message);
                maxUid = Math.max(maxUid, emailUid);
                attachments.addAll(extractAttachments(message, emailUid));
            }
            return new ImapFetchResult(maxUid, attachments);
        } catch (MessagingException | IOException exception) {
            throw new BizException(500, "IMAP 邮件拉取失败: " + config.getEmailAddress(), exception);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    List<EmailAttachment> extractAttachments(Message message, long emailUid) throws MessagingException, IOException {
        String sender = extractSender(message);
        String subject = message.getSubject();
        List<EmailAttachment> attachments = new ArrayList<>();
        collectAttachments(message, emailUid, sender, subject, attachments);
        return attachments;
    }

    private void collectAttachments(Part part, long emailUid, String sender, String subject,
                                    List<EmailAttachment> attachments) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (fileName != null && !fileName.isBlank()
                && !Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
            byte[] content = part.getInputStream().readAllBytes();
            attachments.add(new EmailAttachment(
                    emailUid, sender, subject, fileName, mimeType(part), content));
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                collectAttachments(multipart.getBodyPart(index), emailUid, sender, subject, attachments);
            }
        }
    }

    private static String mimeType(Part part) throws MessagingException {
        String contentType = part.getContentType();
        int parameterIndex = contentType.indexOf(';');
        return parameterIndex >= 0 ? contentType.substring(0, parameterIndex).trim() : contentType;
    }

    private static String extractSender(Message message) throws MessagingException {        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return "";
        }
        Address first = from[0];
        if (first instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return first.toString();
    }

    private static void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException ignored) {
                // 拉取失败时忽略关闭异常
            }
        }
    }

    private static void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException ignored) {
                // 拉取失败时忽略关闭异常
            }
        }
    }
}

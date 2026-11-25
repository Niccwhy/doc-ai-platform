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
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

@Component
public class JakartaImapMailReader implements ImapMailReader {

    private static final int DEFAULT_IMAP_PORT = 993;
    private static final int CONNECTION_TIMEOUT_MS = 10_000;
    private static final String INBOX = "INBOX";

    @Override
    public ImapDiscoveryResult discover(DeptEmailConfig config, long lastUid) {
        int port = config.getImapPort() == null ? DEFAULT_IMAP_PORT : config.getImapPort();
        Properties properties = imapProperties(config, port);
        Session session = Session.getInstance(properties);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore("imaps");
            store.connect(config.getImapHost(), port, config.getEmailAddress(), config.getAuthCode());
            folder = store.getFolder(INBOX);
            folder.open(Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;
            long uidValidity = uidFolder.getUIDValidity();
                long effectiveLastUid = config.getUidValidity() != null
                    && config.getUidValidity() != uidValidity ? 0L : lastUid;
                Message[] messages = uidFolder.getMessagesByUID(effectiveLastUid + 1, UIDFolder.LASTUID);
                long maxUid = effectiveLastUid;
            List<EmailMessageMetadata> metadata = new ArrayList<>();
            if (messages != null) {
                for (Message message : messages) {
                    long uid = uidFolder.getUID(message);
                    maxUid = Math.max(maxUid, uid);
                    metadata.add(extractMessageMetadata(message, uid));
                }
            }
            return new ImapDiscoveryResult(uidValidity, maxUid, metadata);
        } catch (MessagingException | IOException exception) {
            throw new BizException(500, "IMAP 邮件查询失败: " + config.getEmailAddress(), exception);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    @Override
    public EmailAttachmentContent fetchAttachment(DeptEmailConfig config, long emailUid, String partId) {
        int port = config.getImapPort() == null ? DEFAULT_IMAP_PORT : config.getImapPort();
        Session session = Session.getInstance(imapProperties(config, port));
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore("imaps");
            store.connect(config.getImapHost(), port, config.getEmailAddress(), config.getAuthCode());
            folder = store.getFolder(INBOX);
            folder.open(Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;
            Message message = uidFolder.getMessageByUID(emailUid);
            if (message == null) {
                throw new BizException(404, "邮箱中已找不到该邮件");
            }
            Part part = resolvePart(message, partId);
            String fileName = part.getFileName();
            if (fileName == null || fileName.isBlank()
                    || Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
                throw new BizException(400, "指定邮件部件不是可导入的附件");
            }
            return new EmailAttachmentContent(fileName, mimeType(part), part.getInputStream().readAllBytes());
        } catch (BizException exception) {
            throw exception;
        } catch (MessagingException | IOException exception) {
            throw new BizException(500, "IMAP 附件读取失败: " + config.getEmailAddress(), exception);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    EmailMessageMetadata extractMessageMetadata(Message message, long emailUid)
            throws MessagingException, IOException {
        List<EmailAttachmentMetadata> attachments = new ArrayList<>();
        collectAttachmentMetadata(message, "", attachments);
        LocalDateTime receivedAt = message.getReceivedDate() == null ? null
                : message.getReceivedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new EmailMessageMetadata(emailUid, extractSender(message), message.getSubject(),
                receivedAt, truncateBody(extractTextBody(message)), attachments);
    }

    private void collectAttachmentMetadata(Part part, String partId,
                                          List<EmailAttachmentMetadata> attachments)
            throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (fileName != null && !fileName.isBlank()
                && !Part.INLINE.equalsIgnoreCase(part.getDisposition())) {
            int size = part.getSize();
                attachments.add(new EmailAttachmentMetadata(partId.isEmpty() ? "0" : partId, fileName, mimeType(part),
                    size < 0 ? null : (long) size));
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                String childPartId = partId.isEmpty()
                        ? String.valueOf(index + 1) : partId + "." + (index + 1);
                collectAttachmentMetadata(multipart.getBodyPart(index), childPartId, attachments);
            }
        }
    }

    private String extractTextBody(Part part) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (fileName != null && !fileName.isBlank()) {
            return null;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String text ? text : null;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content instanceof String html ? htmlToText(html) : null;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                String text = extractTextBody(multipart.getBodyPart(index));
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Part resolvePart(Message message, String partId) throws MessagingException, IOException {
        if ("0".equals(partId)) {
            return message;
        }
        if (partId == null || !partId.matches("[1-9][0-9]*(\\.[1-9][0-9]*)*")) {
            throw new BizException(400, "邮件附件标识无效");
        }
        Part current = message;
        for (String indexText : partId.split("\\.")) {
            if (!current.isMimeType("multipart/*")) {
                throw new BizException(404, "邮件中已找不到该附件");
            }
            Multipart multipart = (Multipart) current.getContent();
            int index = Integer.parseInt(indexText) - 1;
            if (index >= multipart.getCount()) {
                throw new BizException(404, "邮件中已找不到该附件");
            }
            current = multipart.getBodyPart(index);
        }
        return current;
    }

    private static String htmlToText(String html) throws IOException {
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleText(char[] data, int position) {
                text.append(data).append(' ');
            }

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                appendLineBreak(tag);
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                appendLineBreak(tag);
            }

            private void appendLineBreak(HTML.Tag tag) {
                if (tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.BR
                        || tag == HTML.Tag.LI || tag == HTML.Tag.TR) {
                    text.append('\n');
                }
            }
        }, true);
        return text.toString().replaceAll("[\\t ]+\\n", "\n").strip();
    }

    private static String truncateBody(String text) {
        if (text == null || text.length() <= 20_000) {
            return text;
        }
        return text.substring(0, 20_000);
    }

    private static Properties imapProperties(DeptEmailConfig config, int port) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", config.getImapHost());
        properties.put("mail.imaps.port", String.valueOf(port));
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.connectiontimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        properties.put("mail.imaps.timeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        return properties;
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

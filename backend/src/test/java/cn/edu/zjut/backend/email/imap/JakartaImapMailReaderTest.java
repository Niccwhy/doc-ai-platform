package cn.edu.zjut.backend.email.imap;

import jakarta.activation.DataHandler;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JakartaImapMailReaderTest {

    private JakartaImapMailReader reader;
    private Session session;

    @BeforeEach
    void setUp() {
        reader = new JakartaImapMailReader();
        session = Session.getInstance(new Properties());
    }

    @Test
    void shouldExtractMessageAndAttachmentMetadata() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("client@example.com"));
        message.setSubject("合同附件");

        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("请查收附件");
        multipart.addBodyPart(textPart);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDisposition(Part.ATTACHMENT);
        attachmentPart.setFileName("合同.pdf");
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(
                "PDF-CONTENT".getBytes(StandardCharsets.UTF_8), "application/pdf")));
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);
        message.saveChanges();

        EmailMessageMetadata metadata = reader.extractMessageMetadata(message, 101L);

        assertThat(metadata.emailUid()).isEqualTo(101L);
        assertThat(metadata.sender()).isEqualTo("client@example.com");
        assertThat(metadata.subject()).isEqualTo("合同附件");
        assertThat(metadata.bodyText()).isEqualTo("请查收附件");
        assertThat(metadata.attachments()).hasSize(1);
        EmailAttachmentMetadata attachment = metadata.attachments().get(0);
        assertThat(attachment.partId()).isEqualTo("2");
        assertThat(attachment.fileName()).isEqualTo("合同.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.size()).isNotNull();
    }

    @Test
    void shouldSkipInlineParts() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setSubject("内嵌图片");

        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart inlinePart = new MimeBodyPart();
        inlinePart.setContent(new byte[]{1, 2, 3}, "image/png");
        inlinePart.setHeader("Content-Disposition", "inline; filename=\"logo.png\"");
        inlinePart.setHeader("Content-ID", "<logo>");
        multipart.addBodyPart(inlinePart);
        message.setContent(multipart);
        message.saveChanges();

        assertThat(reader.extractMessageMetadata(message, 1L).attachments()).isEmpty();
    }

    @Test
    void shouldReturnEmptyForTextOnlyMessage() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setText("plain text only");

        assertThat(reader.extractMessageMetadata(message, 1L).attachments()).isEmpty();
    }

    @Test
    void shouldConvertHtmlOnlyBodyToReadableText() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setContent("<html><body><p>第一段</p><p>第二段</p></body></html>", "text/html; charset=UTF-8");
        message.saveChanges();

        assertThat(reader.extractMessageMetadata(message, 2L).bodyText()).contains("第一段", "第二段");
    }

    @Test
    void shouldAssignStableIdToSinglePartAttachment() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setDisposition(Part.ATTACHMENT);
        message.setFileName("合同.pdf");
        message.setDataHandler(new DataHandler(new ByteArrayDataSource(
                "PDF".getBytes(StandardCharsets.UTF_8), "application/pdf")));
        message.saveChanges();

        EmailMessageMetadata metadata = reader.extractMessageMetadata(message, 3L);

        assertThat(metadata.attachments()).hasSize(1);
        assertThat(metadata.attachments().get(0).partId()).isEqualTo("0");
    }
}

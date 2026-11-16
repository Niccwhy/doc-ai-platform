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
    void shouldExtractAttachmentFromMultipartMessage() throws Exception {
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

        List<EmailAttachment> attachments = reader.extractAttachments(message, 101L);

        assertThat(attachments).hasSize(1);
        EmailAttachment attachment = attachments.get(0);
        assertThat(attachment.emailUid()).isEqualTo(101L);
        assertThat(attachment.sender()).isEqualTo("client@example.com");
        assertThat(attachment.subject()).isEqualTo("合同附件");
        assertThat(attachment.fileName()).isEqualTo("合同.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(new String(attachment.content(), StandardCharsets.UTF_8)).isEqualTo("PDF-CONTENT");
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

        assertThat(reader.extractAttachments(message, 1L)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForTextOnlyMessage() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setText("plain text only");

        assertThat(reader.extractAttachments(message, 1L)).isEmpty();
    }
}

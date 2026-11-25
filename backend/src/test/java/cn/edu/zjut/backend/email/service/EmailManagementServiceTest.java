package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.service.DocumentUploadService;
import cn.edu.zjut.backend.email.dto.EmailMessageResponse;
import cn.edu.zjut.backend.email.entity.EmailAttachmentRecord;
import cn.edu.zjut.backend.email.entity.EmailMessage;
import cn.edu.zjut.backend.email.imap.EmailAttachmentContent;
import cn.edu.zjut.backend.email.imap.EmailAttachmentMetadata;
import cn.edu.zjut.backend.email.imap.EmailMessageMetadata;
import cn.edu.zjut.backend.email.imap.ImapDiscoveryResult;
import cn.edu.zjut.backend.email.imap.ImapMailReader;
import cn.edu.zjut.backend.email.mapper.EmailAttachmentMapper;
import cn.edu.zjut.backend.email.mapper.EmailMessageMapper;
import cn.edu.zjut.backend.security.TenantAccessChecker;
import cn.edu.zjut.backend.security.UserContext;
import cn.edu.zjut.backend.security.UserContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailManagementServiceTest {

    @Mock
    private ImapMailReader imapMailReader;
    @Mock
    private DeptEmailConfigService deptEmailConfigService;
    @Mock
    private EmailMessageMapper emailMessageMapper;
    @Mock
    private EmailAttachmentMapper emailAttachmentMapper;
    @Mock
    private EmailDiscoveryPersistenceService discoveryPersistenceService;
    @Mock
    private DocumentUploadService documentUploadService;
    @Mock
    private TenantAccessChecker tenantAccessChecker;

    private EmailManagementService service;

    @BeforeEach
    void setUp() {
        service = new EmailManagementService(imapMailReader, deptEmailConfigService, emailMessageMapper,
                emailAttachmentMapper, discoveryPersistenceService, documentUploadService, tenantAccessChecker);
        UserContextHolder.set(new UserContext(9L, 3L, "dispatcher", "DISPATCHER"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void discoveryStoresMetadataWithoutDownloadingAttachmentContent() {
        DeptEmailConfig config = config();
        EmailMessageMetadata metadata = new EmailMessageMetadata(11L, "client@example.com", "合同",
                LocalDateTime.now(), "请查收", List.of(
                new EmailAttachmentMetadata("1", "同名.pdf", "application/pdf", 12L),
                new EmailAttachmentMetadata("2", "同名.pdf", "application/pdf", 14L)));
        when(deptEmailConfigService.listEnabledByDept(3L)).thenReturn(List.of(config));
        when(imapMailReader.discover(config, 10L)).thenReturn(new ImapDiscoveryResult(99L, 11L, List.of(metadata)));
        when(discoveryPersistenceService.saveDiscovery(eq(config), eq(99L), eq(11L), anyList(), any()))
                .thenAnswer(invocation -> {
                    EmailMessage message = invocation.<List<EmailMessage>>getArgument(3).get(0);
                    message.setId(100L);
                    return List.of(message);
                });
        when(emailAttachmentMapper.selectList(any())).thenReturn(List.of());

        List<EmailMessageResponse> result = service.discoverNewMessages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).isEqualTo("请查收");
        ArgumentCaptor<Map<Long, List<EmailAttachmentRecord>>> attachmentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(discoveryPersistenceService).saveDiscovery(eq(config), eq(99L), eq(11L), anyList(),
                attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().get(11L)).hasSize(2)
                .extracting(EmailAttachmentRecord::getPartId).containsExactly("1", "2");
        verify(imapMailReader, never()).fetchAttachment(any(), anyLong(), any());
        verify(documentUploadService, never()).uploadEmailAttachment(any(), any(), any());
    }

    @Test
    void selectedAttachmentIsDownloadedAndLinkedToDocumentVersion() {
        DeptEmailConfig config = config();
        EmailMessage message = new EmailMessage();
        message.setId(100L);
        message.setEmailConfigId(config.getId());
        message.setEmailUid(11L);
        message.setDeptId(3L);
        message.setStatus("UNPROCESSED");
        EmailAttachmentRecord attachment = new EmailAttachmentRecord();
        attachment.setId(200L);
        attachment.setEmailId(100L);
        attachment.setPartId("1.2");
        attachment.setAttachmentName("合同.pdf");
        attachment.setStatus("UNPROCESSED");
        when(emailMessageMapper.selectOne(any())).thenReturn(message);
        when(emailAttachmentMapper.selectOne(any())).thenReturn(attachment);
        when(deptEmailConfigService.findByIdAndDept(1L, 3L)).thenReturn(config);
        when(emailAttachmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(imapMailReader.fetchAttachment(config, 11L, "1.2")).thenReturn(
                new EmailAttachmentContent("合同.pdf", "application/pdf", "PDF".getBytes(StandardCharsets.UTF_8)));
        UploadDocumentResponse document = new UploadDocumentResponse(
                300L, 400L, "合同.pdf", "SOURCE", 3L, "a".repeat(64), 1, false);
        when(documentUploadService.uploadEmailAttachment(eq("合同.pdf"), eq("application/pdf"), any()))
                .thenReturn(document);
        when(emailAttachmentMapper.selectCount(any())).thenReturn(0L);

        List<UploadDocumentResponse> result = service.importAttachments(100L, List.of(200L));

        assertThat(result).containsExactly(document);
        assertThat(attachment.getStatus()).isEqualTo("IMPORTED");
        assertThat(attachment.getDocumentVersionId()).isEqualTo(400L);
        verify(imapMailReader).fetchAttachment(config, 11L, "1.2");
        verify(emailMessageMapper).updateById(any(EmailMessage.class));
    }

    private static DeptEmailConfig config() {
        DeptEmailConfig config = new DeptEmailConfig();
        config.setId(1L);
        config.setDeptId(3L);
        config.setEmailAddress("dept@example.com");
        config.setLastUid(10L);
        config.setUidValidity(99L);
        config.setStatus(1);
        return config;
    }
}
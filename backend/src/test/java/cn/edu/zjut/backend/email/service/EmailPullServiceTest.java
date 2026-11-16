package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.email.entity.EmailPending;
import cn.edu.zjut.backend.email.imap.EmailAttachment;
import cn.edu.zjut.backend.email.imap.ImapFetchResult;
import cn.edu.zjut.backend.email.imap.ImapMailReader;
import cn.edu.zjut.backend.email.mapper.EmailPendingMapper;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailPullServiceTest {

    @Mock
    private ImapMailReader imapMailReader;

    @Mock
    private DeptEmailConfigService deptEmailConfigService;

    @Mock
    private EmailPendingService emailPendingService;

    @Mock
    private EmailPendingMapper emailPendingMapper;

    @Mock
    private MinioStorageService minioStorageService;

    private EmailPullService emailPullService;

    @BeforeEach
    void setUp() {
        emailPullService = new EmailPullService(imapMailReader, deptEmailConfigService,
                emailPendingService, emailPendingMapper, minioStorageService);
    }

    @Test
    void shouldPullAttachmentsAndSavePendingRecords() {
        DeptEmailConfig config = config(1L, 3L, 10L);
        EmailAttachment pdf = attachment(11L, "合同.pdf", "application/pdf", "PDF");
        EmailAttachment txt = attachment(12L, "..\\..\\etc\\passwd.txt", "text/plain", "hello");
        when(imapMailReader.fetch(config, 10L)).thenReturn(new ImapFetchResult(12L, List.of(pdf, txt)));
        when(emailPendingMapper.selectCount(any())).thenReturn(0L);
        when(emailPendingService.savePending(eq(config), anyList(), eq(12L)))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());

        int saved = emailPullService.pullConfig(config);

        assertThat(saved).isEqualTo(2);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService, times(2)).putObject(
                keyCaptor.capture(), any(InputStream.class), anyLong(), anyString());
        assertThat(keyCaptor.getAllValues()).allMatch(key -> key.startsWith("temp/3/"));
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.endsWith(".pdf"));
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.endsWith(".txt"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailPending>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailPendingService).savePending(eq(config), listCaptor.capture(), eq(12L));
        List<EmailPending> pendings = listCaptor.getValue();
        assertThat(pendings).hasSize(2);
        assertThat(pendings.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(pendings.get(0).getDeptId()).isEqualTo(3L);
        assertThat(pendings.get(0).getEmailUid()).isEqualTo(11L);
        assertThat(pendings.get(0).getAttachmentName()).isEqualTo("合同.pdf");
        assertThat(pendings.get(0).getAttachmentSize()).isEqualTo(3L);
        assertThat(pendings.get(1).getAttachmentName()).isEqualTo("passwd.txt");
    }

    @Test
    void shouldSkipDuplicatePendingRecords() {
        DeptEmailConfig config = config(1L, 3L, 10L);
        when(imapMailReader.fetch(config, 10L))
                .thenReturn(new ImapFetchResult(12L, List.of(attachment(11L, "合同.pdf", "application/pdf", "PDF"))));
        when(emailPendingMapper.selectCount(any())).thenReturn(1L);
        when(emailPendingService.savePending(eq(config), anyList(), eq(12L))).thenReturn(0);

        int saved = emailPullService.pullConfig(config);

        assertThat(saved).isZero();
        verifyNoInteractions(minioStorageService);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailPending>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailPendingService).savePending(eq(config), listCaptor.capture(), eq(12L));
        assertThat(listCaptor.getValue()).isEmpty();
    }

    @Test
    void shouldDoNothingWhenNoNewMessages() {
        DeptEmailConfig config = config(1L, 3L, 10L);
        when(imapMailReader.fetch(config, 10L)).thenReturn(new ImapFetchResult(10L, List.of()));

        int saved = emailPullService.pullConfig(config);

        assertThat(saved).isZero();
        verifyNoInteractions(emailPendingMapper, emailPendingService, minioStorageService);
    }

    @Test
    void shouldContinueOtherMailboxesWhenOneFails() {
        DeptEmailConfig failing = config(1L, 3L, 10L);
        DeptEmailConfig healthy = config(2L, 4L, 0L);
        when(deptEmailConfigService.listEnabled()).thenReturn(List.of(failing, healthy));
        when(imapMailReader.fetch(failing, 10L)).thenThrow(new BizException(500, "IMAP 连接失败"));
        when(imapMailReader.fetch(healthy, 0L))
                .thenReturn(new ImapFetchResult(1L, List.of(attachment(1L, "a.txt", "text/plain", "abc"))));
        when(emailPendingMapper.selectCount(any())).thenReturn(0L);
        when(emailPendingService.savePending(eq(healthy), anyList(), eq(1L)))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());

        int saved = emailPullService.pullAll();

        assertThat(saved).isEqualTo(1);
        verify(minioStorageService).putObject(anyString(), any(InputStream.class), anyLong(), anyString());
        verify(emailPendingService, never()).savePending(eq(failing), anyList(), anyLong());
    }

    @Test
    void shouldPropagateReaderFailureForSingleMailbox() {
        DeptEmailConfig config = config(1L, 3L, 10L);
        when(imapMailReader.fetch(config, 10L)).thenThrow(new BizException(500, "IMAP 连接失败"));

        assertThatThrownBy(() -> emailPullService.pullConfig(config))
                .isInstanceOf(BizException.class);
    }

    private static DeptEmailConfig config(Long id, Long deptId, Long lastUid) {
        DeptEmailConfig config = new DeptEmailConfig();
        config.setId(id);
        config.setDeptId(deptId);
        config.setEmailAddress("dept" + deptId + "@example.com");
        config.setImapHost("imap.example.com");
        config.setImapPort(993);
        config.setLastUid(lastUid);
        config.setStatus(1);
        return config;
    }

    private static EmailAttachment attachment(long uid, String fileName, String contentType, String content) {
        return new EmailAttachment(uid, "client@example.com", "主题",
                fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
    }
}

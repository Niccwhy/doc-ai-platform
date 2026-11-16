package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.email.entity.EmailPending;
import cn.edu.zjut.backend.email.mapper.EmailPendingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailPendingServiceTest {

    @Mock
    private EmailPendingMapper emailPendingMapper;

    @Mock
    private DeptEmailConfigService deptEmailConfigService;

    private EmailPendingService emailPendingService;

    @BeforeEach
    void setUp() {
        emailPendingService = new EmailPendingService(emailPendingMapper, deptEmailConfigService);
    }

    @Test
    void shouldInsertPendingRecordsAndAdvanceUid() {
        DeptEmailConfig config = new DeptEmailConfig();
        config.setId(1L);

        int saved = emailPendingService.savePending(config, List.of(pending(11L), pending(12L)), 12L);

        assertThat(saved).isEqualTo(2);
        verify(emailPendingMapper, times(2)).insert(org.mockito.ArgumentMatchers.any(EmailPending.class));
        verify(deptEmailConfigService).updateLastUid(1L, 12L);
    }

    @Test
    void shouldStillAdvanceUidWhenNoAttachments() {
        DeptEmailConfig config = new DeptEmailConfig();
        config.setId(1L);

        int saved = emailPendingService.savePending(config, List.of(), 5L);

        assertThat(saved).isZero();
        verifyNoInteractions(emailPendingMapper);
        verify(deptEmailConfigService).updateLastUid(1L, 5L);
    }

    private static EmailPending pending(long emailUid) {
        EmailPending pending = new EmailPending();
        pending.setEmailUid(emailUid);
        return pending;
    }
}

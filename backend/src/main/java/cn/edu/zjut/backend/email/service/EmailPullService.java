package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.common.FileNameUtils;
import cn.edu.zjut.backend.email.entity.EmailPending;
import cn.edu.zjut.backend.email.imap.EmailAttachment;
import cn.edu.zjut.backend.email.imap.ImapFetchResult;
import cn.edu.zjut.backend.email.imap.ImapMailReader;
import cn.edu.zjut.backend.email.mapper.EmailPendingMapper;
import cn.edu.zjut.backend.storage.MinioStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailPullService {

    private static final String PENDING_STATUS = "PENDING";
    private static final String DEFAULT_ATTACHMENT_NAME = "attachment";

    private final ImapMailReader imapMailReader;
    private final DeptEmailConfigService deptEmailConfigService;
    private final EmailPendingService emailPendingService;
    private final EmailPendingMapper emailPendingMapper;
    private final MinioStorageService minioStorageService;

    /**
     * 遍历所有启用的部门邮箱配置，单个邮箱失败不影响其他邮箱。
     *
     * @return 新增的暂存记录数
     */
    public int pullAll() {
        List<DeptEmailConfig> configs = deptEmailConfigService.listEnabled();
        int total = 0;
        for (DeptEmailConfig config : configs) {
            try {
                total += pullConfig(config);
            } catch (Exception exception) {
                log.error("拉取部门邮箱附件失败: {}", config.getEmailAddress(), exception);
            }
        }
        return total;
    }

    /**
     * 拉取单个部门邮箱的新邮件附件，写入 MinIO 临时目录并记录 email_pending。
     */
    public int pullConfig(DeptEmailConfig config) {
        long lastUid = config.getLastUid() == null ? 0L : config.getLastUid();
        ImapFetchResult fetchResult = imapMailReader.fetch(config, lastUid);
        if (fetchResult == null || fetchResult.maxUid() <= lastUid) {
            return 0;
        }

        List<EmailPending> pendings = new ArrayList<>();
        for (EmailAttachment attachment : fetchResult.attachments()) {
            if (isDuplicate(config.getDeptId(), attachment)) {
                continue;
            }
            String tempStorageKey = buildTempStorageKey(config.getDeptId(), attachment.fileName());
            minioStorageService.putObject(tempStorageKey,
                    new ByteArrayInputStream(attachment.content()),
                    attachment.content().length,
                    attachment.contentType());

            EmailPending pending = new EmailPending();
            pending.setDeptId(config.getDeptId());
            pending.setEmailUid(attachment.emailUid());
            pending.setSender(truncate(attachment.sender(), 128));
            pending.setSubject(truncate(attachment.subject(), 255));
            pending.setAttachmentName(truncate(
                    FileNameUtils.sanitize(attachment.fileName(), DEFAULT_ATTACHMENT_NAME), 255));
            pending.setAttachmentSize((long) attachment.content().length);
            pending.setTempStorageKey(tempStorageKey);
            pending.setStatus(PENDING_STATUS);
            pendings.add(pending);
        }
        return emailPendingService.savePending(config, pendings, fetchResult.maxUid());
    }

    private boolean isDuplicate(Long deptId, EmailAttachment attachment) {
        String attachmentName = truncate(
                FileNameUtils.sanitize(attachment.fileName(), DEFAULT_ATTACHMENT_NAME), 255);
        Long count = emailPendingMapper.selectCount(new LambdaQueryWrapper<EmailPending>()
                .eq(EmailPending::getDeptId, deptId)
                .eq(EmailPending::getEmailUid, attachment.emailUid())
                .eq(EmailPending::getAttachmentName, attachmentName));
        return count != null && count > 0;
    }

    private static String buildTempStorageKey(Long deptId, String fileName) {
        String sanitized = FileNameUtils.sanitize(fileName, DEFAULT_ATTACHMENT_NAME);
        return "temp/" + deptId + "/" + UUID.randomUUID() + FileNameUtils.extensionSuffix(sanitized);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}

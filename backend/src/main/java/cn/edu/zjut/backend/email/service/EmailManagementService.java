package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.common.FileNameUtils;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.service.DocumentUploadService;
import cn.edu.zjut.backend.email.dto.EmailAttachmentResponse;
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
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.TenantAccessChecker;
import cn.edu.zjut.backend.security.UserContext;
import cn.edu.zjut.backend.security.UserContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailManagementService {

    private static final String UNPROCESSED = "UNPROCESSED";
    private static final String IMPORTING = "IMPORTING";
    private static final String IMPORTED = "IMPORTED";
    private static final String IGNORED = "IGNORED";
    private static final String FAILED = "FAILED";
    private static final String PROCESSED = "PROCESSED";
    private static final String DEFAULT_FILE_NAME = "attachment";
    private static final long MAX_ATTACHMENT_SIZE = 50L * 1024 * 1024;

    private final ImapMailReader imapMailReader;
    private final DeptEmailConfigService deptEmailConfigService;
    private final EmailMessageMapper emailMessageMapper;
    private final EmailAttachmentMapper emailAttachmentMapper;
    private final EmailDiscoveryPersistenceService discoveryPersistenceService;
    private final DocumentUploadService documentUploadService;
    private final TenantAccessChecker tenantAccessChecker;

    public List<EmailMessageResponse> discoverNewMessages() {
        UserContext user = requireEmailOperator();
        List<EmailMessageResponse> discovered = new ArrayList<>();
        for (DeptEmailConfig config : deptEmailConfigService.listEnabledByDept(user.deptId())) {
            try {
                long lastUid = config.getLastUid() == null ? 0L : config.getLastUid();
                ImapDiscoveryResult result = imapMailReader.discover(config, lastUid);
                boolean uidReset = config.getUidValidity() != null
                        && !config.getUidValidity().equals(result.uidValidity());
                List<EmailMessage> messages = new ArrayList<>();
                Map<Long, List<EmailAttachmentRecord>> attachmentsByUid = new HashMap<>();
                for (EmailMessageMetadata metadata : result.messages()) {
                    EmailMessage message = toEntity(metadata, result.uidValidity(), config.getId());
                    messages.add(message);
                    attachmentsByUid.put(metadata.emailUid(), metadata.attachments().stream()
                            .map(this::toEntity)
                            .toList());
                }
                List<EmailMessage> saved = discoveryPersistenceService.saveDiscovery(
                        config, result.uidValidity(), result.maxUid(), messages, attachmentsByUid);
                for (EmailMessage message : saved) {
                    discovered.add(toResponse(message, findAttachments(message.getId())));
                }
                if (uidReset) {
                    log.info("邮箱 UIDVALIDITY 已变化，已按新 UID 空间重新发现邮件: {}", config.getEmailAddress());
                }
            } catch (Exception exception) {
                log.error("手动查询部门邮箱失败: {}", config.getEmailAddress(), exception);
            }
        }
        return discovered;
    }

    public List<EmailMessageResponse> listMessages(String status) {
        UserContext user = requireEmailOperator();
        LambdaQueryWrapper<EmailMessage> query = new LambdaQueryWrapper<EmailMessage>()
                .eq(EmailMessage::getDeptId, user.deptId())
                .orderByDesc(EmailMessage::getReceivedAt)
                .orderByDesc(EmailMessage::getId);
        if (status != null && !status.isBlank()) {
            query.eq(EmailMessage::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return emailMessageMapper.selectList(query).stream()
                .map(message -> toResponse(message, findAttachments(message.getId())))
                .toList();
    }

    public EmailMessageResponse getMessage(Long emailId) {
        UserContext user = requireEmailOperator();
        EmailMessage message = findMessage(emailId, user.deptId());
        return toResponse(message, findAttachments(message.getId()));
    }

    public List<UploadDocumentResponse> importAttachments(Long emailId, List<Long> attachmentIds) {
        UserContext user = requireEmailOperator();
        EmailMessage message = findMessage(emailId, user.deptId());
        if (IGNORED.equals(message.getStatus())) {
            throw new BizException(409, "已忽略的邮件不能导入附件");
        }
        DeptEmailConfig config = deptEmailConfigService.findByIdAndDept(message.getEmailConfigId(), user.deptId());
        if (config == null) {
            throw new BizException(404, "邮件对应的邮箱配置不存在");
        }
        List<UploadDocumentResponse> imported = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            EmailAttachmentRecord attachment = emailAttachmentMapper.selectOne(
                    new LambdaQueryWrapper<EmailAttachmentRecord>()
                            .eq(EmailAttachmentRecord::getId, attachmentId)
                            .eq(EmailAttachmentRecord::getEmailId, emailId));
            if (attachment == null) {
                throw new BizException(404, "邮件附件不存在");
            }
            if (IMPORTED.equals(attachment.getStatus())) {
                continue;
            }
            if (!UNPROCESSED.equals(attachment.getStatus()) && !FAILED.equals(attachment.getStatus())) {
                throw new BizException(409, "附件当前状态不允许导入");
            }
            if (attachment.getAttachmentSize() != null
                    && attachment.getAttachmentSize() > MAX_ATTACHMENT_SIZE) {
                throw new BizException(413, "邮件附件超过 50 MB 限制");
            }
            int claimed = emailAttachmentMapper.update(null, new LambdaUpdateWrapper<EmailAttachmentRecord>()
                    .eq(EmailAttachmentRecord::getId, attachmentId)
                    .in(EmailAttachmentRecord::getStatus, UNPROCESSED, FAILED)
                    .set(EmailAttachmentRecord::getStatus, IMPORTING));
            if (claimed == 0) {
                throw new BizException(409, "附件正在被处理，请刷新后重试");
            }
            try {
                EmailAttachmentContent content = imapMailReader.fetchAttachment(
                        config, message.getEmailUid(), attachment.getPartId());
                if (content.content().length > MAX_ATTACHMENT_SIZE) {
                    throw new BizException(413, "邮件附件超过 50 MB 限制");
                }
                String fileName = FileNameUtils.sanitize(content.fileName(), DEFAULT_FILE_NAME);
                UploadDocumentResponse document = documentUploadService.uploadEmailAttachment(
                        fileName, content.contentType(), content.content());
                attachment.setStatus(IMPORTED);
                attachment.setDocumentVersionId(document.versionId());
                emailAttachmentMapper.updateById(attachment);
                refreshMessageStatus(emailId);
                imported.add(document);
            } catch (RuntimeException exception) {
                attachment.setStatus(FAILED);
                emailAttachmentMapper.updateById(attachment);
                refreshMessageStatus(emailId);
                throw exception;
            }
        }
        return imported;
    }

    public void ignoreAttachment(Long emailId, Long attachmentId) {
        UserContext user = requireEmailOperator();
        findMessage(emailId, user.deptId());
        int updated = emailAttachmentMapper.update(null, new LambdaUpdateWrapper<EmailAttachmentRecord>()
                .eq(EmailAttachmentRecord::getId, attachmentId)
                .eq(EmailAttachmentRecord::getEmailId, emailId)
                .in(EmailAttachmentRecord::getStatus, UNPROCESSED, FAILED)
                .set(EmailAttachmentRecord::getStatus, IGNORED));
        if (updated == 0) {
            throw new BizException(409, "附件不存在或已处理");
        }
        refreshMessageStatus(emailId);
    }

    public void ignoreMessage(Long emailId) {
        UserContext user = requireEmailOperator();
        EmailMessage message = findMessage(emailId, user.deptId());
        emailAttachmentMapper.update(null, new LambdaUpdateWrapper<EmailAttachmentRecord>()
                .eq(EmailAttachmentRecord::getEmailId, emailId)
                .in(EmailAttachmentRecord::getStatus, UNPROCESSED, FAILED)
                .set(EmailAttachmentRecord::getStatus, IGNORED));
        message.setStatus(IGNORED);
        emailMessageMapper.updateById(message);
    }

    private UserContext requireEmailOperator() {
        UserContext user = UserContextHolder.getRequired();
        tenantAccessChecker.requireAnyRole("ADMIN", "DISPATCHER");
        tenantAccessChecker.requireSameDepartment(user.deptId());
        return user;
    }

    private EmailMessage findMessage(Long emailId, Long deptId) {
        EmailMessage message = emailMessageMapper.selectOne(new LambdaQueryWrapper<EmailMessage>()
                .eq(EmailMessage::getId, emailId)
                .eq(EmailMessage::getDeptId, deptId));
        if (message == null) {
            throw new BizException(404, "邮件不存在");
        }
        return message;
    }

    private List<EmailAttachmentRecord> findAttachments(Long emailId) {
        return emailAttachmentMapper.selectList(new LambdaQueryWrapper<EmailAttachmentRecord>()
                .eq(EmailAttachmentRecord::getEmailId, emailId)
                .orderByAsc(EmailAttachmentRecord::getId));
    }

    private void refreshMessageStatus(Long emailId) {
        Long openCount = emailAttachmentMapper.selectCount(new LambdaQueryWrapper<EmailAttachmentRecord>()
                .eq(EmailAttachmentRecord::getEmailId, emailId)
                .notIn(EmailAttachmentRecord::getStatus, IMPORTED, IGNORED));
        if (openCount != null && openCount == 0) {
            EmailMessage message = new EmailMessage();
            message.setId(emailId);
            message.setStatus(PROCESSED);
            emailMessageMapper.updateById(message);
        }
    }

    private EmailMessage toEntity(EmailMessageMetadata metadata, long uidValidity, Long configId) {
        EmailMessage message = new EmailMessage();
        message.setEmailConfigId(configId);
        message.setUidValidity(uidValidity);
        message.setEmailUid(metadata.emailUid());
        message.setSender(truncate(metadata.sender(), 128));
        message.setSubject(truncate(metadata.subject(), 255));
        message.setReceivedAt(metadata.receivedAt());
        message.setBodyText(metadata.bodyText());
        message.setStatus(metadata.attachments().isEmpty() ? PROCESSED : UNPROCESSED);
        return message;
    }

    private EmailAttachmentRecord toEntity(EmailAttachmentMetadata metadata) {
        EmailAttachmentRecord attachment = new EmailAttachmentRecord();
        attachment.setPartId(metadata.partId());
        attachment.setAttachmentName(truncate(
                FileNameUtils.sanitize(metadata.fileName(), DEFAULT_FILE_NAME), 255));
        attachment.setContentType(truncate(metadata.contentType(), 255));
        attachment.setAttachmentSize(metadata.size());
        attachment.setStatus(UNPROCESSED);
        return attachment;
    }

    private EmailMessageResponse toResponse(EmailMessage message, List<EmailAttachmentRecord> attachments) {
        return new EmailMessageResponse(message.getId(), message.getEmailUid(), message.getSender(),
                message.getSubject(), message.getReceivedAt(), message.getBodyText(), message.getStatus(),
                attachments.stream().map(attachment -> new EmailAttachmentResponse(
                        attachment.getId(), attachment.getPartId(), attachment.getAttachmentName(),
                        attachment.getContentType(), attachment.getAttachmentSize(), attachment.getStatus(),
                        attachment.getDocumentVersionId())).toList());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
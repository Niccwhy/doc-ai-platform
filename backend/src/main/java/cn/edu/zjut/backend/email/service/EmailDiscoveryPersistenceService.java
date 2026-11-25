package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.email.entity.EmailAttachmentRecord;
import cn.edu.zjut.backend.email.entity.EmailMessage;
import cn.edu.zjut.backend.email.mapper.EmailAttachmentMapper;
import cn.edu.zjut.backend.email.mapper.EmailMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailDiscoveryPersistenceService {

    private final EmailMessageMapper emailMessageMapper;
    private final EmailAttachmentMapper emailAttachmentMapper;
    private final DeptEmailConfigService deptEmailConfigService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> saveDiscovery(DeptEmailConfig config, Long uidValidity, long maxUid,
                                            List<EmailMessage> messages,
                                            Map<Long, List<EmailAttachmentRecord>> attachmentsByUid) {
        List<EmailMessage> inserted = new ArrayList<>();
        for (EmailMessage message : messages) {
            EmailMessage existing = emailMessageMapper.selectOne(new LambdaQueryWrapper<EmailMessage>()
                    .select(EmailMessage::getId)
                    .eq(EmailMessage::getEmailConfigId, config.getId())
                    .eq(EmailMessage::getUidValidity, uidValidity)
                    .eq(EmailMessage::getEmailUid, message.getEmailUid())
                .last("LIMIT 1"));
            if (existing != null) {
                continue;
            }
            message.setDeptId(config.getDeptId());
            message.setEmailConfigId(config.getId());
            message.setUidValidity(uidValidity);
            emailMessageMapper.insert(message);
            for (EmailAttachmentRecord attachment : attachmentsByUid.getOrDefault(message.getEmailUid(), List.of())) {
                attachment.setEmailId(message.getId());
                emailAttachmentMapper.insert(attachment);
            }
            inserted.add(message);
        }
        deptEmailConfigService.updateCursor(config.getId(), uidValidity, maxUid);
        return inserted;
    }
}
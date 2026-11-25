package cn.edu.zjut.backend.email.imap;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;

/**
 * 邮件附件读取器，便于单元测试替换真实 IMAP 连接。
 */
public interface ImapMailReader {

    ImapDiscoveryResult discover(DeptEmailConfig config, long lastUid);

    EmailAttachmentContent fetchAttachment(DeptEmailConfig config, long emailUid, String partId);

}

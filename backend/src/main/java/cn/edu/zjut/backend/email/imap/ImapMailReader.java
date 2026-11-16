package cn.edu.zjut.backend.email.imap;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;

/**
 * 邮件附件读取器，便于单元测试替换真实 IMAP 连接。
 */
public interface ImapMailReader {

    /**
     * 拉取 UID 大于 lastUid 的邮件附件。
     */
    ImapFetchResult fetch(DeptEmailConfig config, long lastUid);
}

package cn.edu.zjut.backend.email.scheduler;

import cn.edu.zjut.backend.email.service.EmailPullService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "email.imap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailPullScheduler {

    private final EmailPullService emailPullService;

    @Scheduled(
            initialDelayString = "${email.imap.initial-delay-ms:60000}",
            fixedDelayString = "${email.imap.poll-interval-ms:60000}")
    public void pullScheduled() {
        try {
            int count = emailPullService.pullAll();
            if (count > 0) {
                log.info("邮件附件拉取完成，新增暂存记录 {} 条", count);
            }
        } catch (Exception exception) {
            log.error("定时拉取邮件附件失败", exception);
        }
    }
}

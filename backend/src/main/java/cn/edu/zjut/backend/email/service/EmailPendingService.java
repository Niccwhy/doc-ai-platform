package cn.edu.zjut.backend.email.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.service.DeptEmailConfigService;
import cn.edu.zjut.backend.email.entity.EmailPending;
import cn.edu.zjut.backend.email.mapper.EmailPendingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailPendingService {

    private final EmailPendingMapper emailPendingMapper;
    private final DeptEmailConfigService deptEmailConfigService;

    /**
     * 暂存记录与邮箱 last_uid 在同一事务内提交，避免重复拉取。
     */
    @Transactional
    public int savePending(DeptEmailConfig config, List<EmailPending> pendings, long latestUid) {
        for (EmailPending pending : pendings) {
            emailPendingMapper.insert(pending);
        }
        deptEmailConfigService.updateLastUid(config.getId(), latestUid);
        return pendings.size();
    }
}

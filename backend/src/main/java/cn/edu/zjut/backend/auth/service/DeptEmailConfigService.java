package cn.edu.zjut.backend.auth.service;

import cn.edu.zjut.backend.auth.entity.DeptEmailConfig;
import cn.edu.zjut.backend.auth.mapper.DeptEmailConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeptEmailConfigService {

    private final DeptEmailConfigMapper deptEmailConfigMapper;

    public List<DeptEmailConfig> listEnabled() {
        return deptEmailConfigMapper.selectList(new LambdaQueryWrapper<DeptEmailConfig>()
                .eq(DeptEmailConfig::getStatus, 1)
                .orderByAsc(DeptEmailConfig::getId));
    }

    public void updateLastUid(Long configId, long lastUid) {
        DeptEmailConfig update = new DeptEmailConfig();
        update.setId(configId);
        update.setLastUid(lastUid);
        deptEmailConfigMapper.updateById(update);
    }
}

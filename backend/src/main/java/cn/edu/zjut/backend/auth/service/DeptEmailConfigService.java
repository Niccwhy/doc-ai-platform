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

    public List<DeptEmailConfig> listEnabledByDept(Long deptId) {
        return deptEmailConfigMapper.selectList(new LambdaQueryWrapper<DeptEmailConfig>()
                .eq(DeptEmailConfig::getDeptId, deptId)
                .eq(DeptEmailConfig::getStatus, 1)
                .orderByAsc(DeptEmailConfig::getId));
    }

    public DeptEmailConfig findByIdAndDept(Long configId, Long deptId) {
        return deptEmailConfigMapper.selectOne(new LambdaQueryWrapper<DeptEmailConfig>()
                .eq(DeptEmailConfig::getId, configId)
                .eq(DeptEmailConfig::getDeptId, deptId)
                .last("LIMIT 1"));
    }

    public void updateLastUid(Long configId, long lastUid) {
        DeptEmailConfig update = new DeptEmailConfig();
        update.setId(configId);
        update.setLastUid(lastUid);
        deptEmailConfigMapper.updateById(update);
    }

    public void updateCursor(Long configId, Long uidValidity, long lastUid) {
        DeptEmailConfig update = new DeptEmailConfig();
        update.setId(configId);
        update.setUidValidity(uidValidity);
        update.setLastUid(lastUid);
        deptEmailConfigMapper.updateById(update);
    }
}

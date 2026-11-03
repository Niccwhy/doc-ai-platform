package cn.edu.zjut.backend.config;

import cn.edu.zjut.backend.entity.SysDept;
import cn.edu.zjut.backend.entity.SysUser;
import cn.edu.zjut.backend.mapper.SysDeptMapper;
import cn.edu.zjut.backend.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 初始化数据：默认部门与账号（幂等）。
 * <p>
 * 数据库不可用时不阻断应用启动（仅告警），便于先起服务再排查环境。
 * 默认密码来自配置，首次登录后请及时修改。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean createDemoUsers;

    public DataInitializer(SysDeptMapper deptMapper,
                           SysUserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.init.admin-username:admin}") String adminUsername,
                           @Value("${app.init.admin-password:admin123}") String adminPassword,
                           @Value("${app.init.demo-users:true}") boolean createDemoUsers) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.createDemoUsers = createDemoUsers;
    }

    @Override
    public void run(String... args) {
        try {
            Long deptId = ensureDept();
            ensureUser(adminUsername, adminPassword, "系统管理员", "ADMIN", deptId);
            if (createDemoUsers) {
                ensureUser("dispatcher", "123456", "演示分配员", "DISPATCHER", deptId);
                ensureUser("processor", "123456", "演示处理员", "PROCESSOR", deptId);
            }
            log.info("初始化检查完成，默认部门 id={}", deptId);
        } catch (Exception e) {
            log.warn("初始化数据失败（数据库可能未就绪）: {}", e.getMessage());
        }
    }

    private Long ensureDept() {
        SysDept dept = deptMapper.selectOne(new QueryWrapper<SysDept>()
                .isNull("deleted_at").orderByAsc("id").last("LIMIT 1"));
        if (dept != null) {
            return dept.getId();
        }
        SysDept created = new SysDept();
        created.setName("总部");
        // 根部门用 NULL 表示（原 0 哨兵由数据库触发器归一，现已移除，改由应用层负责）
        created.setParentId(null);
        created.setCreatedAt(OffsetDateTime.now());
        deptMapper.insert(created);
        log.info("已创建默认部门：总部");
        return created.getId();
    }

    private void ensureUser(String username, String rawPassword, String realName,
                            String role, Long deptId) {
        Long count = userMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("username", username).isNull("deleted_at"));
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setDeptId(deptId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        userMapper.insert(user);
        log.info("已创建初始账号：{}（角色 {}）", username, role);
    }
}

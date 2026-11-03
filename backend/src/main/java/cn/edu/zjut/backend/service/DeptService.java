package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.entity.SysDept;
import cn.edu.zjut.backend.entity.SysUser;
import cn.edu.zjut.backend.mapper.SysDeptMapper;
import cn.edu.zjut.backend.mapper.SysUserMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 部门（租户）管理。仅系统管理员可增删改，普通用户只能查看本部门。
 */
@Service
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public DeptService(SysDeptMapper deptMapper, SysUserMapper userMapper) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    public List<SysDept> list() {
        QueryWrapper<SysDept> wrapper = new QueryWrapper<SysDept>()
                .isNull("deleted_at").orderByAsc("id");
        if (!CurrentUser.isAdmin()) {
            wrapper.eq("id", CurrentUser.deptId());
        }
        return deptMapper.selectList(wrapper);
    }

    public SysDept get(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return dept;
    }

    public SysDept create(String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "部门名称不能为空");
        }
        // 对外仍接受 0 表示根部门，落库统一为 NULL（数据库不再做 0→NULL 归一）
        Long normalizedParent = (parentId == null || parentId == 0L) ? null : parentId;
        if (normalizedParent != null) {
            SysDept parent = deptMapper.selectById(normalizedParent);
            if (parent == null || parent.getDeletedAt() != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "上级部门不存在");
            }
        }
        // 同父级下不重名（与数据库 uq_dept_parent_name 语义一致）
        Long exists = deptMapper.selectCount(new QueryWrapper<SysDept>()
                .eq("name", name.trim())
                .eq(normalizedParent == null, "parent_id", normalizedParent)
                .isNull("deleted_at"));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "同级下已存在同名部门");
        }
        SysDept dept = new SysDept();
        dept.setName(name.trim());
        dept.setParentId(normalizedParent);
        dept.setCreatedAt(OffsetDateTime.now());
        deptMapper.insert(dept);
        return dept;
    }

    public SysDept rename(Long id, String name) {
        SysDept dept = get(id);
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "部门名称不能为空");
        }
        String trimmed = name.trim();
        // 同级重名检查（排除自身）
        Long exists = deptMapper.selectCount(new QueryWrapper<SysDept>()
                .eq("name", trimmed)
                .eq(dept.getParentId() == null, "parent_id", dept.getParentId())
                .ne("id", id)
                .isNull("deleted_at"));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "同级下已存在同名部门");
        }
        dept.setName(trimmed);
        deptMapper.updateById(dept);
        return dept;
    }

    /**
     * 软删除。部门下仍有用户时拒绝，避免出现无归属账号。
     */
    public void delete(Long id) {
        get(id);
        if (id.equals(CurrentUser.deptId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除自己所属的部门");
        }
        Long users = userMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("dept_id", id).isNull("deleted_at"));
        if (users != null && users > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该部门下仍有用户，无法删除");
        }
        Long children = deptMapper.selectCount(new QueryWrapper<SysDept>()
                .eq("parent_id", id).isNull("deleted_at"));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该部门下仍有子部门，请先处理子部门");
        }
        SysDept dept = get(id);
        dept.setDeletedAt(OffsetDateTime.now());
        deptMapper.updateById(dept);
    }
}

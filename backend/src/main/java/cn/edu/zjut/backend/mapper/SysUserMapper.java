package cn.edu.zjut.backend.mapper;

import cn.edu.zjut.backend.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户表 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

package cn.edu.zjut.backend.mapper;

import cn.edu.zjut.backend.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务表 Mapper。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}

package cn.edu.zjut.backend.mapper;

import cn.edu.zjut.backend.entity.TaskDocumentRel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务-源文件关联表 Mapper。
 */
@Mapper
public interface TaskDocumentRelMapper extends BaseMapper<TaskDocumentRel> {
}

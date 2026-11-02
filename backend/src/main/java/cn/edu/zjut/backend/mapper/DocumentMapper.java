package cn.edu.zjut.backend.mapper;

import cn.edu.zjut.backend.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档表 Mapper。继承 BaseMapper 即可获得 CRUD 能力。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}

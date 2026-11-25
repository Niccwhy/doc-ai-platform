package cn.edu.zjut.backend.email.mapper;

import cn.edu.zjut.backend.email.entity.EmailAttachmentRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailAttachmentMapper extends BaseMapper<EmailAttachmentRecord> {
}
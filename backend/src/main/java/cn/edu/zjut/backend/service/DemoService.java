package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.DemoDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 演示用 Service。后续业务逻辑都放在 service 层，controller 只做参数与返回值编排。
 */
@Service
public class DemoService {

    public DemoDto getDemo(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 必须为正整数");
        }
        return new DemoDto(id, "示例数据-" + id, LocalDateTime.now());
    }

}

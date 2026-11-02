package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.DemoDto;
import cn.edu.zjut.backend.service.DemoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示接口，用于验证统一返回体 + 全局异常处理。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    /**
     * 正常分支：返回统一成功体。
     */
    @GetMapping("/ok")
    public Result<DemoDto> ok(@RequestParam(defaultValue = "1") Long id) {
        return Result.ok(demoService.getDemo(id));
    }

    /**
     * 异常分支：id 非法时抛业务异常，由全局异常处理转成统一失败体。
     */
    @GetMapping("/err")
    public Result<DemoDto> err(@RequestParam(defaultValue = "-1") Long id) {
        return Result.ok(demoService.getDemo(id));
    }

}

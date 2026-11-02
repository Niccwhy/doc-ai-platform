package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.service.ParseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档解析接口。上传文档，返回解析后的统一结构（片段列表）。
 */
@RestController
@RequestMapping("/api/parse")
public class ParseController {

    private final ParseService parseService;

    public ParseController(ParseService parseService) {
        this.parseService = parseService;
    }

    @PostMapping
    public Result<DocParseResult> parse(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        try {
            return Result.ok(parseService.parse(file.getBytes(), file.getOriginalFilename()));
        } catch (IOException e) {
            return Result.fail(500, "读取文件失败: " + e.getMessage());
        }
    }

}

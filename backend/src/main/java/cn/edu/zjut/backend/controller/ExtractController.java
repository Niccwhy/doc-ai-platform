package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.service.ExtractService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 信息抽取接口（框架版）。真实抽取需配置 LLM api-key。
 */
@RestController
@RequestMapping("/api/extract")
public class ExtractController {

    private final ExtractService extractService;

    public ExtractController(ExtractService extractService) {
        this.extractService = extractService;
    }

    /**
     * LLM 网关状态探活。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(extractService.llmStatus());
    }

    /**
     * 从给定上下文抽取字段。
     * body: { "context": "...", "fields": ["甲方","金额"], "instruction": "..." }
     */
    @PostMapping
    public Result<Map<String, String>> extract(@RequestBody Map<String, Object> body) {
        String context = String.valueOf(body.getOrDefault("context", ""));
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) body.getOrDefault("fields", List.of());
        String instruction = String.valueOf(body.getOrDefault("instruction", ""));
        return Result.ok(extractService.extract(context, fields, instruction));
    }
}

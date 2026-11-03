package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.ExtractPersistRequest;
import cn.edu.zjut.backend.entity.ExtractResult;
import cn.edu.zjut.backend.service.ExtractionPipelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 抽取入库与结果审核接口。
 * <ul>
 *   <li>POST /api/extract/persist            执行抽取并带证据入库</li>
 *   <li>GET  /api/extract/results            查询某文档的抽取结果</li>
 *   <li>POST /api/extract/results/{id}/review 人工确认/驳回</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/extract")
public class ExtractionResultController {

    private final ExtractionPipelineService pipelineService;

    public ExtractionResultController(ExtractionPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/persist")
    public Result<Map<String, Object>> persist(@RequestBody ExtractPersistRequest request) {
        return Result.ok(pipelineService.extractAndSave(
                request.documentId(), request.fields(), request.instruction()));
    }

    @GetMapping("/results")
    public Result<List<ExtractResult>> results(@RequestParam("documentId") Long documentId) {
        return Result.ok(pipelineService.listByDocument(documentId));
    }

    @PostMapping("/results/{id}/review")
    public Result<ExtractResult> review(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        boolean verified = Boolean.parseBoolean(String.valueOf(body.getOrDefault("verified", "true")));
        return Result.ok(pipelineService.review(id, verified));
    }
}

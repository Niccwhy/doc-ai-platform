package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.ExtractPersistRequest;
import cn.edu.zjut.backend.entity.ExtractResult;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.entity.TaskDocumentRel;
import cn.edu.zjut.backend.mapper.TaskDocumentRelMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import cn.edu.zjut.backend.service.ExtractionPipelineService;
import cn.edu.zjut.backend.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 抽取入库与结果审核接口。
 * <ul>
 *   <li>POST /api/extract/persist            执行抽取并带证据入库（同步，自动建任务挂源文件）</li>
 *   <li>GET  /api/extract/results            查询某文档的抽取结果</li>
 *   <li>POST /api/extract/results/{id}/review 人工确认/驳回</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/extract")
public class ExtractionResultController {

    private final ExtractionPipelineService pipelineService;
    private final TaskService taskService;
    private final TaskDocumentRelMapper taskDocumentRelMapper;

    public ExtractionResultController(ExtractionPipelineService pipelineService,
                                      TaskService taskService,
                                      TaskDocumentRelMapper taskDocumentRelMapper) {
        this.pipelineService = pipelineService;
        this.taskService = taskService;
        this.taskDocumentRelMapper = taskDocumentRelMapper;
    }

    @PostMapping("/persist")
    public Result<Map<String, Object>> persist(@RequestBody ExtractPersistRequest request) {
        // 抽取结果要求挂 task：先建任务 + 挂源文件，再执行抽取
        Long taskId = taskService.submit(Task.TYPE_EXTRACT, "{}");
        attachSource(taskId, request.documentId());
        return Result.ok(pipelineService.extractAndSave(
                taskId, request.documentId(), request.schemaId(), request.fields(), request.instruction()));
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

    private void attachSource(Long taskId, Long documentId) {
        TaskDocumentRel rel = new TaskDocumentRel();
        rel.setTaskId(taskId);
        rel.setDocumentId(documentId);
        rel.setDeptId(CurrentUser.deptId());
        rel.setOrd(0);
        rel.setCreatedAt(OffsetDateTime.now());
        taskDocumentRelMapper.insert(rel);
    }
}

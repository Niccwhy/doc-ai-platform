package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.service.AsyncJobService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 业务异步任务接口（抽取 / 填表），提交即返回 taskId，进度走 /api/tasks/{id} 或 SSE。
 */
@RestController
@RequestMapping("/api/tasks")
public class AsyncJobController {

    private final AsyncJobService asyncJobService;

    public AsyncJobController(AsyncJobService asyncJobService) {
        this.asyncJobService = asyncJobService;
    }

    /**
     * 提交抽取任务。
     * body: { "documentId": 1, "instruction": "只抽取合同类字段" }
     */
    @PostMapping("/extract")
    public Result<Map<String, Object>> submitExtract(@RequestBody Map<String, Object> body) {
        Object raw = body.get("documentId");
        if (!(raw instanceof Number number)) {
            return Result.fail(400, "documentId 必须是数字");
        }
        Long schemaId = body.get("schemaId") instanceof Number s ? s.longValue() : null;
        String instruction = String.valueOf(body.getOrDefault("instruction", ""));
        Long taskId = asyncJobService.submitExtract(number.longValue(), schemaId, instruction);
        asyncJobService.runExtract(taskId, number.longValue(), schemaId, instruction);
        return Result.ok(Map.of("taskId", taskId));
    }

    /**
     * 提交填表任务（上传模板）。
     */
    @PostMapping("/fill")
    public Result<Map<String, Object>> submitFill(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            String fileName = file.getOriginalFilename();
            Long taskId = asyncJobService.submitFill(fileName);
            asyncJobService.runFill(taskId, bytes, fileName);
            return Result.ok(Map.of("taskId", taskId));
        } catch (java.io.IOException e) {
            return Result.fail(500, "读取上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 下载任务产物（填表结果）。
     */
    @GetMapping("/{taskId}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long taskId) {
        AsyncJobService.ExportFile file = asyncJobService.exportFile(taskId);
        String contentType = file.fileName().endsWith(".docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"result\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .body(file.content());
    }
}

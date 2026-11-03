package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.UploadFile;
import cn.edu.zjut.backend.service.IngestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 批量上传接口：一次接收多个样本文件，异步入库，返回 taskId。
 * <p>
 * 注意：必须在请求线程内把文件读成字节再交给异步任务，
 * 否则请求结束后容器清理 multipart 临时文件，异步线程读取会失败。
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 批量上传文件（multipart，字段名 files 可传多个）。
     * 返回 taskId，前端通过 /api/tasks/{taskId} 轮询或 SSE 订阅进度。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> ingest(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Result.fail(400, "至少上传一个文件");
        }

        List<UploadFile> uploads = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            for (MultipartFile f : files) {
                String name = f.getOriginalFilename();
                uploads.add(new UploadFile(name, f.getBytes(), f.getContentType()));
                names.add(name);
            }
        } catch (IOException e) {
            return Result.fail(500, "读取上传文件失败: " + e.getMessage());
        }

        String taskId = ingestService.submit(names);
        ingestService.ingestBatch(taskId, uploads);
        return Result.ok(Map.of("taskId", taskId));
    }
}

package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.dto.UploadFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量上传与建库编排：一次接收多个文件，逐个「存储 → 解析 → 索引入库」，
 * 全程异步并推送进度（复用任务中心）。是赛题「16 样本一次上传 + 跨文档检索」的入口。
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final StorageService storageService;
    private final ParseService parseService;
    private final IndexingService indexingService;
    private final TaskService taskService;
    /** 仅用于任务入参/结果序列化，使用本地实例避免依赖 Spring 的 Jackson 自动配置 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestService(StorageService storageService,
                         ParseService parseService,
                         IndexingService indexingService,
                         TaskService taskService) {
        this.storageService = storageService;
        this.parseService = parseService;
        this.indexingService = indexingService;
        this.taskService = taskService;
    }

    /**
     * 提交批量上传任务，立即返回 taskId。
     */
    public String submit(List<String> fileNames) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("files", fileNames));
            return taskService.submit("INGEST", payload);
        } catch (Exception e) {
            throw new RuntimeException("序列化任务入参失败", e);
        }
    }

    /**
     * 异步执行批量入库。
     * <p>
     * 入参是已读入内存的 {@link UploadFile} 而非 MultipartFile：
     * 请求结束后容器会清理 multipart 临时文件，异步线程再读会失败。
     */
    @Async("taskExecutor")
    public void ingestBatch(String taskId, List<UploadFile> files) {
        taskService.updateProgress(taskId, "RUNNING", 5);
        int total = files.size();
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;

        for (int i = 0; i < total; i++) {
            UploadFile file = files.get(i);
            String name = file.fileName();
            try {
                // 1. 存储到对象存储
                String objectKey = storageService.uploadBytes(
                        file.content(), name, file.contentType());
                // 2. 解析
                DocParseResult parsed = parseService.parse(file.content(), name);
                // 3. 索引入库 + 向量化
                String fileType = parsed.fileType();
                Long docId = indexingService.index(
                        name, fileType, objectKey, file.content().length, parsed);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("file", name);
                r.put("documentId", docId);
                r.put("chunks", parsed.chunkCount());
                r.put("status", "OK");
                results.add(r);
                ok++;
            } catch (Exception e) {
                log.error("文件 {} 入库失败", name, e);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("file", name);
                r.put("status", "FAILED");
                r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                results.add(r);
            }
            int progress = 5 + (int) ((i + 1) * 90.0 / total);
            taskService.updateProgress(taskId, "RUNNING", progress);
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", total);
            result.put("succeeded", ok);
            result.put("failed", total - ok);
            result.put("files", results);
            // 全部失败时任务标记为 FAILED，避免误导调用方
            taskService.complete(taskId, ok > 0 ? "SUCCESS" : "FAILED",
                    objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            taskService.fail(taskId, e.getMessage());
        }
    }
}

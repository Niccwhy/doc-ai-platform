package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.FillOutcome;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.entity.TaskDocumentRel;
import cn.edu.zjut.backend.mapper.TaskDocumentRelMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 业务异步任务：抽取与填表接入任务中心，提交即返回任务 id，后台执行并推送 SSE 进度。
 * <p>
 * 填表产物写入本地导出目录，通过下载接口取回（不依赖对象存储，便于离线验证）。
 */
@Service
public class AsyncJobService {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobService.class);

    private final TaskService taskService;
    private final ExtractionPipelineService pipelineService;
    private final TableFillService tableFillService;
    private final TaskDocumentRelMapper taskDocumentRelMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path exportDir;

    public AsyncJobService(TaskService taskService,
                           ExtractionPipelineService pipelineService,
                           TableFillService tableFillService,
                           TaskDocumentRelMapper taskDocumentRelMapper,
                           @Value("${doc.export.dir:./data/exports}") String exportDir) {
        this.taskService = taskService;
        this.pipelineService = pipelineService;
        this.tableFillService = tableFillService;
        this.taskDocumentRelMapper = taskDocumentRelMapper;
        this.exportDir = Path.of(exportDir);
    }

    /**
     * 提交抽取任务，返回任务 id。
     */
    public Long submitExtract(Long documentId, Long schemaId, String instruction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", documentId);
        payload.put("schemaId", schemaId);
        payload.put("instruction", instruction == null ? "" : instruction);
        return taskService.submit(Task.TYPE_EXTRACT, toJson(payload));
    }

    /**
     * 异步执行抽取：挂源文件 → 检索 → 抽取 → 校验 → 入库。
     */
    @Async("taskExecutor")
    public void runExtract(Long taskId, Long documentId, Long schemaId, String instruction) {
        try {
            taskService.updateProgress(taskId, Task.STATUS_PROCESSING, 10);
            attachSourceDocument(taskId, documentId);
            Map<String, Object> summary = pipelineService.extractAndSave(
                    taskId, documentId, schemaId, null, instruction);
            taskService.complete(taskId, Task.STATUS_COMPLETED, toJson(summary));
        } catch (Exception e) {
            log.error("抽取任务失败: {}", taskId, e);
            taskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 把源文件挂到任务下（extract_result 要求结果属于任务源文件清单）。
     */
    private void attachSourceDocument(Long taskId, Long documentId) {
        TaskDocumentRel rel = new TaskDocumentRel();
        rel.setTaskId(taskId);
        rel.setDocumentId(documentId);
        rel.setDeptId(CurrentUser.deptId());
        rel.setOrd(0);
        rel.setCreatedAt(OffsetDateTime.now());
        taskDocumentRelMapper.insert(rel);
    }

    /**
     * 提交填表任务，返回任务 id。
     */
    public Long submitFill(String fileName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileName", fileName);
        return taskService.submit(Task.TYPE_FILL, toJson(payload));
    }

    /**
     * 异步执行填表：识别表头 → 抽取 → 回填 → 落盘导出。
     */
    @Async("taskExecutor")
    public void runFill(Long taskId, byte[] templateBytes, String fileName) {
        try {
            taskService.updateProgress(taskId, Task.STATUS_PROCESSING, 20);
            FillOutcome outcome = tableFillService.fillAndExport(templateBytes, fileName);
            taskService.updateProgress(taskId, Task.STATUS_PROCESSING, 80);

            Files.createDirectories(exportDir);
            Path out = exportDir.resolve(taskId + "." + outcome.format());
            Files.write(out, outcome.content());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("format", outcome.format());
            result.put("filledCount", outcome.filledCount());
            result.put("unmatched", outcome.unmatchedFields());
            result.put("downloadPath", "/api/tasks/" + taskId + "/file");
            taskService.complete(taskId, Task.STATUS_COMPLETED, toJson(result));
        } catch (Exception e) {
            log.error("填表任务失败: {}", taskId, e);
            taskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 读取任务导出文件。
     */
    public ExportFile exportFile(Long taskId) {
        if (taskId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "任务号不能为空");
        }
        if (!Files.exists(exportDir)) {
            throw new BizException(ResultCode.NOT_FOUND, "暂无导出文件");
        }
        try (Stream<Path> files = Files.list(exportDir)) {
            Path file = files
                    .filter(p -> p.getFileName().toString().startsWith(taskId + "."))
                    .findFirst()
                    .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "结果文件不存在或已清理"));
            return new ExportFile(Files.readAllBytes(file), file.getFileName().toString());
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取结果文件失败: " + e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化任务数据失败", e);
        }
    }

    /**
     * 导出文件内容 + 文件名。
     */
    public record ExportFile(byte[] content, String fileName) {
    }
}

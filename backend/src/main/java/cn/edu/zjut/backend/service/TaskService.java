package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.mapper.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步任务服务：提交任务立即返回 taskId，后台线程执行并更新进度，通过 SSE 推送。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;
    // taskId -> SseEmitter，用于实时推送进度
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 提交任务：状态先落库（PENDING），立即返回 taskId。
     */
    public String submit(String taskType, String payload) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setPayload(payload);
        task.setCreatedAt(OffsetDateTime.now());
        taskMapper.insert(task);
        return task.getTaskId();
    }

    /**
     * 异步执行任务（示例：模拟分步耗时任务）。
     * 实际业务（解析/抽取/填表）会替换这里的 execute 逻辑。
     */
    @Async("taskExecutor")
    public void executeAsync(String taskId, Runnable work) {
        Task task = findByTaskId(taskId);
        try {
            update(taskId, "RUNNING", 5, null, OffsetDateTime.now(), null);
            work.run();
            update(taskId, "SUCCESS", 100, null, null, OffsetDateTime.now());
        } catch (Exception e) {
            log.error("任务执行失败: {}", taskId, e);
            update(taskId, "FAILED", task.getProgress(), e.getMessage(), null, OffsetDateTime.now());
        }
    }

    /**
     * 示例：模拟一个分阶段任务，每阶段更新进度并推送。
     */
    @Async("taskExecutor")
    public void runDemoTask(String taskId, int totalSteps) {
        Task task = findByTaskId(taskId);
        try {
            for (int i = 1; i <= totalSteps; i++) {
                int progress = (int) ((double) i / totalSteps * 100);
                update(taskId, "RUNNING", progress, null,
                        i == 1 ? OffsetDateTime.now() : null, null);
                Thread.sleep(500); // 模拟耗时
            }
            update(taskId, "SUCCESS", 100, null, null, OffsetDateTime.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(taskId, "FAILED", task.getProgress(), "任务被中断", null, OffsetDateTime.now());
        } catch (Exception e) {
            log.error("演示任务失败: {}", taskId, e);
            update(taskId, "FAILED", task.getProgress(), e.getMessage(), null, OffsetDateTime.now());
        }
    }

    /**
     * 查询任务状态。
     */
    public Task get(String taskId) {
        return findByTaskId(taskId);
    }

    /**
     * 更新任务状态与进度并推送 SSE（供外部业务在异步执行中回调）。
     */
    public void updateProgress(String taskId, String status, int progress) {
        update(taskId, status, progress, null, null, null);
    }

    /**
     * 更新任务状态、进度与结果（结果以 JSON 字符串存入 result 字段）并推送 SSE。
     */
    public void complete(String taskId, String status, String resultJson) {
        Task task = findByTaskId(taskId);
        task.setStatus(status);
        task.setProgress("SUCCESS".equals(status) ? 100 : task.getProgress());
        task.setResult(resultJson);
        task.setFinishedAt(OffsetDateTime.now());
        taskMapper.updateById(task);
        push(taskId, status, task.getProgress());
    }

    /**
     * 标记任务失败并记录错误信息。
     */
    public void fail(String taskId, String errorMsg) {
        update(taskId, "FAILED", 100, errorMsg == null ? "未知错误" : errorMsg, null, OffsetDateTime.now());
    }

    /**
     * 订阅 SSE：前端建立长连接接收进度推送。
     */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError(e -> emitters.remove(taskId));

        // 立即推送当前状态
        Task task = findByTaskId(taskId);
        push(taskId, task.getStatus(), task.getProgress());
        return emitter;
    }

    private void update(String taskId, String status, int progress, String errorMsg,
                        OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        Task task = findByTaskId(taskId);
        task.setStatus(status);
        task.setProgress(progress);
        if (errorMsg != null) {
            task.setErrorMsg(errorMsg);
        }
        if (startedAt != null) {
            task.setStartedAt(startedAt);
        }
        if (finishedAt != null) {
            task.setFinishedAt(finishedAt);
        }
        taskMapper.updateById(task);

        // 推送 SSE
        push(taskId, status, progress);
    }

    private void push(String taskId, String status, int progress) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(Map.of("taskId", taskId, "status", status, "progress", progress)));
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("taskId", taskId, "status", status)));
                emitter.complete();
                emitters.remove(taskId);
            }
        } catch (IOException e) {
            emitters.remove(taskId);
        }
    }

    private Task findByTaskId(String taskId) {
        Task task = taskMapper.selectOne(new QueryWrapper<Task>().eq("task_id", taskId));
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }
}

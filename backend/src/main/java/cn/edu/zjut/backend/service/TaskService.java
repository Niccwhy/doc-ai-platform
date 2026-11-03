package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.mapper.TaskMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步任务服务：提交任务立即返回任务 id（Long），后台线程执行并更新进度，通过 SSE 推送。
 *
 * <p>v3 对齐说明：任务号即 {@code task.id}（自增），状态机用
 * PENDING / PROCESSING / COMPLETED / FAILED（对齐 {@code ck_task_status}）。
 * 已废弃 v1 的字符串 task_id 与 RUNNING/SUCCESS 状态。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;
    // taskId(Long) -> SseEmitter，用于实时推送进度
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 提交任务：状态先落库（PENDING），立即返回任务 id。
     */
    public Long submit(String taskType, String payloadJson) {
        Task task = new Task();
        task.setDeptId(CurrentUser.deptId());
        task.setCreatorId(CurrentUser.id());
        task.setTaskType(taskType);
        task.setStatus(Task.STATUS_PENDING);
        task.setProgress(0);
        task.setPriority(5);
        task.setPayloadJson(payloadJson);
        task.setAttemptCount(0);
        task.setVersionNo(0);
        task.setCreatedAt(OffsetDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }

    /**
     * 查询任务状态。
     */
    public Task get(Long taskId) {
        return requireTask(taskId);
    }

    /**
     * 更新任务状态与进度并推送 SSE（供业务在异步执行中回调）。
     */
    public void updateProgress(Long taskId, String status, int progress) {
        update(taskId, status, progress, null, null, null);
    }

    /**
     * 更新任务为完成态（进度置 100、记录结束时间与结果）。
     */
    public void complete(Long taskId, String status, String resultJson) {
        Task task = requireTask(taskId);
        task.setStatus(status);
        if (Task.STATUS_COMPLETED.equals(status)) {
            task.setProgress(100);
        }
        task.setResultJson(resultJson);
        task.setCompletedAt(OffsetDateTime.now());
        taskMapper.updateById(task);
        push(taskId, status, task.getProgress());
    }

    /**
     * 标记任务失败并记录错误信息。
     */
    public void fail(Long taskId, String errorMsg) {
        update(taskId, Task.STATUS_FAILED, 100,
                errorMsg == null ? "未知错误" : errorMsg, null, OffsetDateTime.now());
    }

    /**
     * 订阅 SSE：前端建立长连接接收进度推送。
     */
    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError(e -> emitters.remove(taskId));

        // 立即推送当前状态
        Task task = requireTask(taskId);
        push(taskId, task.getStatus(), task.getProgress());
        return emitter;
    }

    private void update(Long taskId, String status, int progress, String errorMsg,
                        OffsetDateTime startedAt, OffsetDateTime completedAt) {
        Task task = requireTask(taskId);
        task.setStatus(status);
        task.setProgress(progress);
        if (errorMsg != null) {
            task.setErrorMsg(errorMsg);
        }
        if (startedAt != null) {
            task.setStartedAt(startedAt);
        }
        if (completedAt != null) {
            task.setCompletedAt(completedAt);
        }
        taskMapper.updateById(task);

        push(taskId, status, progress);
    }

    private void push(Long taskId, String status, int progress) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(Map.of("taskId", taskId, "status", status, "progress", progress)));
            if (Task.STATUS_COMPLETED.equals(status) || Task.STATUS_FAILED.equals(status)) {
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

    private Task requireTask(Long taskId) {
        if (taskId == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }
}

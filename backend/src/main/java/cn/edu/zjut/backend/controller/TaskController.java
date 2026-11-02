package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.service.TaskService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 异步任务接口：提交（返回 taskId）、查询、SSE 订阅。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 提交一个演示任务（模拟耗时，用于验证异步链路）。
     * 返回 202 语义：用 code 区分，实际 HTTP 仍 200，便于统一返回体。
     */
    @PostMapping
    public Result<Map<String, String>> submit(@RequestBody(required = false) Map<String, Object> body) {
        int steps = 5;
        if (body != null && body.get("steps") instanceof Number n) {
            steps = n.intValue();
        }
        String taskId = taskService.submit("DEMO", "{}");
        taskService.runDemoTask(taskId, steps);
        return Result.ok(Map.of("taskId", taskId));
    }

    /**
     * 查询任务状态。
     */
    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> get(@PathVariable String taskId) {
        Task task = taskService.get(taskId);
        return Result.ok(Map.of(
                "taskId", task.getTaskId(),
                "status", task.getStatus(),
                "progress", task.getProgress(),
                "errorMsg", task.getErrorMsg() == null ? "" : task.getErrorMsg()
        ));
    }

    /**
     * 订阅 SSE 进度推送。
     */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        return taskService.subscribe(taskId);
    }
}

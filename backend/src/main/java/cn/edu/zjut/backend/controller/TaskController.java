package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.entity.Task;
import cn.edu.zjut.backend.service.TaskService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 异步任务接口：查询、SSE 订阅（任务号即 task.id，Long 自增）。
 * 任务的提交入口在具体业务控制器（抽取/填表/上传）。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 查询任务状态。
     */
    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> get(@PathVariable Long taskId) {
        Task task = taskService.get(taskId);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "progress", task.getProgress(),
                "errorMsg", task.getErrorMsg() == null ? "" : task.getErrorMsg()
        ));
    }

    /**
     * 订阅 SSE 进度推送。
     */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long taskId) {
        return taskService.subscribe(taskId);
    }
}

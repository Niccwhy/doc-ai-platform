package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.service.DbService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据库探活接口，验证方向 4（PostgreSQL + pgvector + MyBatis-Plus）连通。
 */
@RestController
@RequestMapping("/api/db")
public class DbController {

    private final DbService dbService;

    public DbController(DbService dbService) {
        this.dbService = dbService;
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(dbService.ping());
    }

}

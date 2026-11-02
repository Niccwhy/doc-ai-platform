package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.mapper.DocumentMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库探活服务，验证 PostgreSQL 连通、MyBatis-Plus 与 pgvector 扩展可用。
 */
@Service
public class DbService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentMapper documentMapper;

    public DbService(JdbcTemplate jdbcTemplate, DocumentMapper documentMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentMapper = documentMapper;
    }

    public Map<String, Object> ping() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 数据库版本
        String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
        result.put("dbVersion", version);

        // 2. 表数量
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
        result.put("tableCount", tableCount);

        // 3. pgvector 扩展是否可用
        String ext = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        result.put("pgvector", ext);

        // 4. MyBatis-Plus 是否可用（能查到 document 表行数即说明 mapper 注册成功）
        Long docCount = documentMapper.selectCount(null);
        result.put("documentCount", docCount);

        return result;
    }

}

package cn.edu.zjut.backend.document.mapper;

import cn.edu.zjut.backend.document.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 仅在同一部门内按当前版本的 SHA-256 查找文件，实现部门隔离的秒传去重。
     */
    @Select("""
            SELECT d.*
            FROM document d
            JOIN document_version v ON v.document_id = d.id
            WHERE d.dept_id = #{deptId}
              AND d.deleted_at IS NULL
              AND v.sha256 = #{sha256}
              AND v.is_current = 1
            ORDER BY d.id
            LIMIT 1
            """)
    Document selectBySha256InDept(@Param("deptId") Long deptId, @Param("sha256") String sha256);
}

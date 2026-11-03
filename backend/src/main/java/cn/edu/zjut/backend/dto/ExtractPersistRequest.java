package cn.edu.zjut.backend.dto;

import cn.edu.zjut.backend.entity.ExtractField;

import java.util.List;

/**
 * 抽取入库请求。
 *
 * @param documentId  文档 id
 * @param instruction 额外抽取指令（可选）
 * @param schemaId    使用的 schema id（可选；为空则使用全部激活 schema 的字段）
 * @param fields      字段定义（可选；为空则使用库中全部字段）
 */
public record ExtractPersistRequest(
        Long documentId,
        String instruction,
        Long schemaId,
        List<ExtractField> fields
) {
}

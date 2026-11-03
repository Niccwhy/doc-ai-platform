package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.ValidationResult;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.entity.ExtractResult;
import cn.edu.zjut.backend.mapper.ExtractResultMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import cn.edu.zjut.backend.service.extract.ExtractionValidator;
import cn.edu.zjut.backend.service.llm.LLMClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息提取流水线：字段定义（来自 schema）→ 跨文档检索 → LLM 抽取 → 校验归一 → 带证据入库。
 * <p>
 * 校验不通过的结果同样入库（is_verified=false、置信度偏低），交由人工复核，
 * 符合赛题「不确定即标注、证据链必填」的要求。
 * <p>
 * v3 对齐：结果必须挂 task（extract_result.task_id NOT NULL），由调用方先建任务。
 */
@Service
public class ExtractionPipelineService {

    private static final int EVIDENCE_MAX_LENGTH = 2000;

    private final LLMClient llmClient;
    private final RetrievalService retrievalService;
    private final ExtractSchemaService schemaService;
    private final ExtractResultMapper resultMapper;
    private final ExtractionValidator validator = new ExtractionValidator();

    public ExtractionPipelineService(LLMClient llmClient,
                                     RetrievalService retrievalService,
                                     ExtractSchemaService schemaService,
                                     ExtractResultMapper resultMapper) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
        this.schemaService = schemaService;
        this.resultMapper = resultMapper;
    }

    /**
     * 对指定文档执行抽取并入库。
     *
     * @param taskId      归属任务 id（必填；结果挂到该任务下）
     * @param documentId  文档 id（必填）
     * @param schemaId    使用的 schema；为空则使用全部激活 schema 的字段
     * @param fields      显式字段定义；优先级最高，为空则回退到 schema
     * @param instruction 额外抽取指令
     * @return 汇总（总数 / 通过数 / 待复核数 / 明细）
     */
    public Map<String, Object> extractAndSave(Long taskId, Long documentId, Long schemaId,
                                              List<ExtractField> fields, String instruction) {
        if (documentId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "documentId 不能为空");
        }
        List<ExtractField> targets = resolveFields(schemaId, fields);
        if (targets.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有可用的字段定义，请先创建 Schema");
        }

        List<Map<String, Object>> details = new ArrayList<>();
        int acceptedCount = 0;

        for (ExtractField field : targets) {
            String query = buildQuery(field);
            List<String> contexts = retrievalService.retrieve(query, 3);
            String context = String.join("\n\n", contexts);

            Map<String, String> extracted = llmClient.extract(context, List.of(field.getName()), instruction);
            String rawValue = extracted.get(field.getName());

            ValidationResult validation = validator.validate(
                    rawValue, context, field.getDataType(), field.getUnit());

            ExtractResult result = new ExtractResult();
            result.setDeptId(CurrentUser.deptId());
            result.setTaskId(taskId);
            result.setDocumentId(documentId);
            result.setFieldKey(field.getKey() == null ? field.getName() : field.getKey());
            result.setValueType(normalizeValueType(field.getDataType()));
            result.setFieldValue(validation.normalizedValue());
            result.setRawValue(rawValue);
            result.setConfidence(validation.confidence());
            result.setEvidenceText(truncate(context));
            result.setIsSelected(false);
            result.setIsModified(false);
            result.setIsVerified(false);
            result.setCreatedAt(OffsetDateTime.now());
            result.setUpdatedAt(OffsetDateTime.now());
            resultMapper.insert(result);

            if (validation.accepted()) {
                acceptedCount++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resultId", result.getId());
            item.put("field", field.getName());
            item.put("value", validation.normalizedValue());
            item.put("accepted", validation.accepted());
            item.put("confidence", validation.confidence());
            item.put("reason", validation.reason());
            details.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", taskId);
        summary.put("documentId", documentId);
        summary.put("total", targets.size());
        summary.put("accepted", acceptedCount);
        summary.put("pendingReview", targets.size() - acceptedCount);
        summary.put("llmAvailable", llmClient.isAvailable());
        summary.put("results", details);
        return summary;
    }

    /**
     * 查询某文档的抽取结果。
     */
    public List<ExtractResult> listByDocument(Long documentId) {
        return resultMapper.selectList(new QueryWrapper<ExtractResult>()
                .eq("document_id", documentId)
                .eq("dept_id", CurrentUser.deptId())
                .orderByAsc("id"));
    }

    /**
     * 人工确认 / 驳回某条结果。
     */
    public ExtractResult review(Long resultId, boolean verified) {
        ExtractResult result = resultMapper.selectById(resultId);
        if (result == null) {
            throw new BizException(ResultCode.NOT_FOUND, "抽取结果不存在: " + resultId);
        }
        if (!result.getDeptId().equals(CurrentUser.deptId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他部门的抽取结果");
        }
        result.setIsVerified(verified);
        result.setIsModified(true);
        result.setModifiedBy(CurrentUser.id());
        result.setModifiedAt(OffsetDateTime.now());
        result.setUpdatedAt(OffsetDateTime.now());
        resultMapper.updateById(result);
        return result;
    }

    private List<ExtractField> resolveFields(Long schemaId, List<ExtractField> fields) {
        if (fields != null && !fields.isEmpty()) {
            return fields;
        }
        if (schemaId != null) {
            return schemaService.fieldsOf(schemaService.get(schemaId));
        }
        return schemaService.activeFields();
    }

    private String buildQuery(ExtractField field) {
        if (field.getAlias() == null || field.getAlias().isBlank()) {
            return field.getName();
        }
        return field.getName() + " " + field.getAlias().replace(",", " ").replace("，", " ");
    }

    private String truncate(String evidence) {
        if (evidence == null) {
            return "";
        }
        return evidence.length() <= EVIDENCE_MAX_LENGTH
                ? evidence
                : evidence.substring(0, EVIDENCE_MAX_LENGTH);
    }

    /**
     * 把字段类型（string/number/date/boolean）映射为 extract_result.value_type。
     */
    private String normalizeValueType(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return "TEXT";
        }
        return switch (dataType.toLowerCase()) {
            case "number" -> "NUMBER";
            case "date" -> "DATE";
            case "boolean" -> "BOOLEAN";
            case "enum" -> "ENUM";
            default -> "TEXT";
        };
    }
}

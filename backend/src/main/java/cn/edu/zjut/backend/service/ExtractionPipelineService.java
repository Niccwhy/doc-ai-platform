package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.ValidationResult;
import cn.edu.zjut.backend.entity.ExtractField;
import cn.edu.zjut.backend.entity.ExtractResult;
import cn.edu.zjut.backend.mapper.ExtractFieldMapper;
import cn.edu.zjut.backend.mapper.ExtractResultMapper;
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
 * 信息提取流水线：字段定义 → 跨文档检索 → LLM 抽取 → 校验归一 → 带证据入库。
 * <p>
 * 校验不通过的结果同样入库（verified=false、置信度偏低），交由人工复核，
 * 符合赛题「不确定即标注、证据链必填」的要求。
 */
@Service
public class ExtractionPipelineService {

    private static final int EVIDENCE_MAX_LENGTH = 2000;

    private final LLMClient llmClient;
    private final RetrievalService retrievalService;
    private final ExtractFieldMapper fieldMapper;
    private final ExtractResultMapper resultMapper;
    private final ExtractionValidator validator = new ExtractionValidator();

    public ExtractionPipelineService(LLMClient llmClient,
                                     RetrievalService retrievalService,
                                     ExtractFieldMapper fieldMapper,
                                     ExtractResultMapper resultMapper) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
        this.fieldMapper = fieldMapper;
        this.resultMapper = resultMapper;
    }

    /**
     * 对指定文档执行抽取并入库。
     *
     * @param documentId  文档 id（必填）
     * @param fields      字段定义；为空则使用库中全部字段
     * @param instruction 额外抽取指令
     * @return 汇总（总数 / 通过数 / 待复核数 / 明细）
     */
    public Map<String, Object> extractAndSave(Long documentId, List<ExtractField> fields, String instruction) {
        if (documentId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "documentId 不能为空");
        }
        List<ExtractField> targets = (fields == null || fields.isEmpty())
                ? fieldMapper.selectList(new QueryWrapper<ExtractField>().orderByAsc("id"))
                : fields;
        if (targets.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有可用的字段定义，请先通过 /api/fields 创建字段");
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
            result.setDocumentId(documentId);
            result.setFieldId(field.getId());
            result.setFieldName(field.getName());
            result.setValue(validation.normalizedValue());
            result.setRawValue(rawValue);
            result.setConfidence(validation.confidence());
            result.setEvidence(truncate(context));
            result.setVerified(false);
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
        result.setVerified(verified);
        result.setUpdatedAt(OffsetDateTime.now());
        resultMapper.updateById(result);
        return result;
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
}

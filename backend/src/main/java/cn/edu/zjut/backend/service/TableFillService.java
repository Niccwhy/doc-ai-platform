package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.FillOutcome;
import cn.edu.zjut.backend.dto.TableFillResult;
import cn.edu.zjut.backend.service.fill.TemplateFillEngine;
import cn.edu.zjut.backend.service.llm.LLMClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板表格自动填写服务（Excel / Word 模板）。
 * <p>
 * 流程：识别模板表头 → 逐字段「检索 + LLM 抽取」→ 按坐标保真回填 → 输出可下载文件。
 * 无 LLM Key 时抽取值全为 null，仍可走通导出链路（用于验证模板定位与回填是否准确）。
 */
@Service
public class TableFillService {

    private final LLMClient llmClient;
    private final RetrievalService retrievalService;
    private final TemplateFillEngine fillEngine = new TemplateFillEngine();

    public TableFillService(LLMClient llmClient, RetrievalService retrievalService) {
        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
    }

    /**
     * 识别模板表头字段（支持 xlsx / docx）。
     */
    public List<String> parseHeaders(MultipartFile file) {
        try {
            return parseHeaders(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "读取模板失败: " + e.getMessage());
        }
    }

    /**
     * 识别模板表头字段（字节数组版本，供异步任务复用）。
     */
    public List<String> parseHeaders(byte[] bytes, String fileName) {
        try {
            return fillEngine.detectHeaders(bytes, fileName);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "读取模板失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 逐字段抽取，返回带证据的明细。
     */
    public List<TableFillResult.FieldFill> extractWithEvidence(List<String> fields) {
        List<TableFillResult.FieldFill> results = new ArrayList<>();
        for (String field : fields) {
            List<String> contexts = retrievalService.retrieve(field, 3);
            String context = String.join("\n\n", contexts);

            Map<String, String> extracted = llmClient.extract(context, List.of(field), null);
            String value = extracted.get(field);

            String source;
            if (value != null && !value.isBlank()) {
                source = llmClient.isAvailable() ? "llm" : "unverified";
            } else {
                source = "null";
            }

            results.add(new TableFillResult.FieldFill(
                    field, value, source, null, context.isBlank() ? "" : context));
        }
        return results;
    }

    /**
     * 只返回字段值映射（供评测比对）。
     */
    public TableFillResult fill(List<String> fields, String fileName) {
        return new TableFillResult(fileName, fields.size(), extractWithEvidence(fields));
    }

    /**
     * 完整链路：识别表头 → 抽取 → 保真回填 → 返回文件字节与写入明细。
     */
    public FillOutcome fillAndExport(MultipartFile file) {
        try {
            return fillAndExport(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "读取模板失败: " + e.getMessage());
        }
    }

    /**
     * 完整链路（字节数组版本，供异步任务复用）。
     */
    public FillOutcome fillAndExport(byte[] templateBytes, String fileName) {
        List<String> headers = parseHeaders(templateBytes, fileName);
        if (headers.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "模板中未识别到表头字段");
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (TableFillResult.FieldFill f : extractWithEvidence(headers)) {
            if (f.value() != null && !f.value().isBlank()) {
                values.put(f.fieldName(), f.value());
            }
        }

        try {
            return fillEngine.fill(templateBytes, fileName, values);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "回填模板失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, e.getMessage());
        }
    }
}

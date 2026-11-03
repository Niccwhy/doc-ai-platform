package cn.edu.zjut.backend.service.docop;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.ChangeRecord;
import cn.edu.zjut.backend.dto.DocIntent;
import cn.edu.zjut.backend.dto.DocPreview;
import cn.edu.zjut.backend.dto.ExecutionReport;
import cn.edu.zjut.backend.dto.VersionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档智能操作编排：解析 → 白名单校验 → 副本试执行 → 预览 → 确认落版本 → 可回滚。
 * <p>
 * 设计要点：原文件永不被直接修改；预览副本存本地，确认后才写为新版本。
 */
@Service
public class DocOperationService {

    private static final Logger log = LoggerFactory.getLogger(DocOperationService.class);

    private final DocIntentParser parser = new DocIntentParser();
    private final DocCommandValidator validator = new DocCommandValidator();
    private final DocxCommandExecutor executor = new DocxCommandExecutor();
    private final DocumentVersionStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path previewDir;

    public DocOperationService(@Value("${doc.version.dir:./data/versions}") String versionDir) {
        this.store = new DocumentVersionStore(versionDir);
        this.previewDir = Path.of(versionDir).resolve("_preview");
    }

    /**
     * 在副本上试执行，返回变更摘要与预览令牌（不改动任何已保存版本）。
     */
    public DocPreview preview(MultipartFile file, String instruction) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".docx")) {
            throw new BizException(ResultCode.BAD_REQUEST, "文档操作目前仅支持 .docx 文件");
        }
        String docId = DocumentVersionStore.safeDocId(fileName);

        DocIntent intent = parser.parse(instruction);
        if (intent.commands().isEmpty()) {
            // 识别为提取/表格类或未识别：不产生变更，直接回传原因
            return new DocPreview(null, docId, intent.intent(), intent.message(),
                    List.of(), 0, false);
        }

        List<String> errors = validator.validate(intent.commands());
        if (!errors.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, String.join("；", errors));
        }

        ExecutionReport report;
        try {
            report = executor.execute(file.getBytes(), intent.commands());
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "文档执行失败: " + e.getMessage());
        }

        String token = UUID.randomUUID().toString();
        writePreview(token, report.content(), docId, fileName, instruction);

        List<ChangeRecord> changes = report.changes();
        return new DocPreview(token, docId, intent.intent(), intent.message(),
                changes, report.affected(), report.affected() > 0);
    }

    /**
     * 确认执行：把预览副本保存为新版本。
     */
    public Map<String, Object> apply(String token) {
        PreviewMeta meta = readMeta(token);
        byte[] content = readPreviewContent(token);
        int version = store.save(meta.docId(), content, meta.instruction());
        deletePreview(token);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", meta.docId());
        result.put("fileName", meta.fileName());
        result.put("version", version);
        result.put("note", meta.instruction());
        return result;
    }

    /**
     * 预览副本内容（供前端下载查看）。
     */
    public byte[] previewContent(String token) {
        return readPreviewContent(token);
    }

    public List<VersionInfo> versions(String docId) {
        return store.list(docId);
    }

    /**
     * 回滚到指定历史版本（生成新版本，不丢历史）。
     */
    public Map<String, Object> rollback(String docId, int version) {
        int newVersion = store.rollback(docId, version);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", docId);
        result.put("rolledBackTo", version);
        result.put("newVersion", newVersion);
        return result;
    }

    public byte[] download(String docId, int version) {
        try {
            return store.load(docId, version);
        } catch (IllegalArgumentException e) {
            // 版本不存在属于客户端请求问题，返回 404 而非 500
            throw new BizException(ResultCode.NOT_FOUND, e.getMessage());
        }
    }

    // ==================== 预览暂存 ====================

    private void writePreview(String token, byte[] content, String docId, String fileName, String instruction) {
        try {
            Files.createDirectories(previewDir);
            Files.write(previewDir.resolve(token + ".docx"), content);
            PreviewMeta meta = new PreviewMeta(docId, fileName, instruction);
            Files.write(previewDir.resolve(token + ".json"), objectMapper.writeValueAsBytes(meta));
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存预览副本失败: " + e.getMessage());
        }
    }

    private PreviewMeta readMeta(String token) {
        validateToken(token);
        Path metaFile = previewDir.resolve(token + ".json");
        if (!Files.exists(metaFile)) {
            throw new BizException(ResultCode.NOT_FOUND, "预览已失效，请重新生成预览");
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(metaFile), PreviewMeta.class);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取预览信息失败: " + e.getMessage());
        }
    }

    private byte[] readPreviewContent(String token) {
        validateToken(token);
        Path file = previewDir.resolve(token + ".docx");
        if (!Files.exists(file)) {
            throw new BizException(ResultCode.NOT_FOUND, "预览已失效，请重新生成预览");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取预览文件失败: " + e.getMessage());
        }
    }

    private void deletePreview(String token) {
        try {
            Files.deleteIfExists(previewDir.resolve(token + ".docx"));
            Files.deleteIfExists(previewDir.resolve(token + ".json"));
        } catch (IOException e) {
            log.warn("清理预览副本失败: {}", e.getMessage());
        }
    }

    /**
     * 令牌必须是标准 UUID，防止路径穿越。
     */
    private void validateToken(String token) {
        if (token == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "预览令牌不能为空");
        }
        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "预览令牌格式非法");
        }
    }

    /**
     * 预览元信息。
     */
    public record PreviewMeta(String docId, String fileName, String instruction) {
    }
}

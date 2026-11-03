package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.DocPreview;
import cn.edu.zjut.backend.dto.VersionInfo;
import cn.edu.zjut.backend.service.docop.DocOperationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 文档智能操作接口：自然语言指令 → 解析 → 白名单校验 → 副本预览 → 确认落版本 → 回滚。
 */
@RestController
@RequestMapping("/api/doc")
public class DocOperationController {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocOperationService docOperationService;

    public DocOperationController(DocOperationService docOperationService) {
        this.docOperationService = docOperationService;
    }

    /**
     * 试执行并返回变更摘要（不修改已保存版本）。
     */
    @PostMapping("/preview")
    public Result<DocPreview> preview(@RequestParam("file") MultipartFile file,
                                      @RequestParam("instruction") String instruction) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        return Result.ok(docOperationService.preview(file, instruction));
    }

    /**
     * 下载预览副本，供用户在确认前查看效果。
     */
    @GetMapping("/preview/{token}/download")
    public ResponseEntity<byte[]> downloadPreview(@PathVariable String token) {
        byte[] content = docOperationService.previewContent(token);
        return docxResponse(content, "preview-" + token.substring(0, 8) + ".docx");
    }

    /**
     * 确认执行：把预览结果保存为新版本。
     */
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        String token = String.valueOf(body.getOrDefault("token", ""));
        return Result.ok(docOperationService.apply(token));
    }

    /**
     * 版本列表。
     */
    @GetMapping("/{docId}/versions")
    public Result<List<VersionInfo>> versions(@PathVariable String docId) {
        return Result.ok(docOperationService.versions(docId));
    }

    /**
     * 回滚到指定历史版本（生成新版本，不丢历史）。
     */
    @PostMapping("/{docId}/rollback")
    public Result<Map<String, Object>> rollback(@PathVariable String docId,
                                                @RequestBody Map<String, Object> body) {
        Object raw = body.get("version");
        if (!(raw instanceof Number number)) {
            return Result.fail(400, "version 必须是数字");
        }
        return Result.ok(docOperationService.rollback(docId, number.intValue()));
    }

    /**
     * 下载指定版本。
     */
    @GetMapping("/{docId}/versions/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String docId,
                                           @PathVariable int version) {
        byte[] content = docOperationService.download(docId, version);
        return docxResponse(content, docId + "-v" + version + ".docx");
    }

    private ResponseEntity<byte[]> docxResponse(byte[] content, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"document.docx\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(DOCX_MIME))
                .body(content);
    }
}

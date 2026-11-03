package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.FillOutcome;
import cn.edu.zjut.backend.dto.TableFillResult;
import cn.edu.zjut.backend.service.TableFillService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 模板表格自动填写接口。
 * <ul>
 *   <li>POST /api/fill          返回逐字段填充明细（JSON，便于比对与调参）</li>
 *   <li>POST /api/fill/export   返回回填后的成品文件（保留原模板样式）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/fill")
public class TableFillController {

    private final TableFillService tableFillService;

    public TableFillController(TableFillService tableFillService) {
        this.tableFillService = tableFillService;
    }

    /**
     * 上传模板并填充，返回逐字段明细（字段名 / 值 / 来源 / 证据）。
     */
    @PostMapping
    public Result<TableFillResult> fill(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        List<String> headers = tableFillService.parseHeaders(file);
        if (headers.isEmpty()) {
            return Result.fail(400, "模板中未识别到表头字段");
        }
        return Result.ok(tableFillService.fill(headers, file.getOriginalFilename()));
    }

    /**
     * 上传模板 → 抽取 → 回填 → 下载成品文件。
     * <p>
     * 响应头附带 X-Fill-Count（写入字段数）与 X-Fill-Unmatched（未匹配字段，URL 编码）。
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        FillOutcome outcome = tableFillService.fillAndExport(file);

        String contentType = "docx".equals(outcome.format())
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String outName = buildOutputName(outcome.fileName());
        String encodedName = URLEncoder.encode(outName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"filled." + outcome.format() + "\"; filename*=UTF-8''" + encodedName)
                .header("X-Fill-Count", String.valueOf(outcome.filledCount()))
                .header("X-Fill-Unmatched", URLEncoder.encode(
                        String.join(",", outcome.unmatchedFields()), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(contentType))
                .body(outcome.content());
    }

    private String buildOutputName(String original) {
        if (original == null || original.isBlank()) {
            return "filled";
        }
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot) : "";
        return base + "-已填写" + ext;
    }
}

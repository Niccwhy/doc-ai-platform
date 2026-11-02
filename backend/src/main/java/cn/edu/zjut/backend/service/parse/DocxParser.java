package cn.edu.zjut.backend.service.parse;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.dto.DocParseResult.Chunk;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * docx 解析器：按文档顺序遍历段落与表格，记录标题路径、表格坐标。
 */
@Component
public class DocxParser implements DocParser {

    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    @Override
    public DocParseResult parse(byte[] bytes, String fileName) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>(); // 标题路径栈

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            int tableIndex = 0;
            // 按 body 元素顺序遍历（段落与表格交叉出现）
            var bodyElements = doc.getBodyElements();
            int seq = 0;
            for (var element : bodyElements) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle();
                    // 标题识别：样式含 Heading 或 TOCHeading，或样式为 heading 级别
                    if (style != null && (style.startsWith("Heading") || style.startsWith("TOC"))) {
                        int level = parseHeadingLevel(style);
                        while (headingStack.size() >= level && !headingStack.isEmpty()) {
                            headingStack.remove(headingStack.size() - 1);
                        }
                        headingStack.add(text.trim());
                        // 标题本身也作为一个 text chunk
                        chunks.add(new Chunk(seq++, "text", joinPath(headingStack), text.trim(), null, null, null));
                    } else {
                        chunks.add(new Chunk(seq++, "text", joinPath(headingStack), text.trim(), null, null, null));
                    }
                } else if (element instanceof XWPFTable table) {
                    tableIndex++;
                    List<List<String>> data = new ArrayList<>();
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText().trim());
                        }
                        data.add(cells);
                    }
                    int rowCount = data.size();
                    int colCount = data.isEmpty() ? 0 : data.get(0).size();
                    String cellRef = "table" + tableIndex + "!A1:" + colName(colCount) + rowCount;
                    String content = serializeTable(data);
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("rows", String.valueOf(rowCount));
                    meta.put("cols", String.valueOf(colCount));
                    chunks.add(new Chunk(seq++, "table", joinPath(headingStack), content, cellRef, data, meta));
                }
            }
        }
        return new DocParseResult(fileName, "docx", chunks.size(), chunks);
    }

    private int parseHeadingLevel(String style) {
        // 形如 Heading1 / Heading2 / Heading 1
        String digits = style.replaceAll("\\D+", "");
        return digits.isEmpty() ? 1 : Integer.parseInt(digits);
    }

    private String joinPath(List<String> stack) {
        return String.join(" > ", stack);
    }

    private String colName(int n) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }

    private String serializeTable(List<List<String>> data) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : data) {
            sb.append(String.join(" | ", row)).append("\n");
        }
        return sb.toString().trim();
    }
}

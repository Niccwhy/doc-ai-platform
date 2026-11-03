package cn.edu.zjut.backend.service.fill;

import cn.edu.zjut.backend.dto.FillOutcome;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模板保真回填引擎（纯 POI，无 Spring 依赖，便于单元测试）。
 * <p>
 * 策略：
 * <ul>
 *   <li>自动定位表头行（xlsx 取前 10 行中非空单元格最多者；docx 取表格前 3 行中匹配字段最多者）</li>
 *   <li>表头与字段按 {@link FieldMatcher} 归一化匹配，容忍"注册资本（万元）"这类带单位写法</li>
 *   <li>只写值，不动样式：已有单元格直接 setCellValue；新单元格继承同行样式</li>
 *   <li>公式单元格不覆盖；合并单元格写到区域左上角</li>
 * </ul>
 */
public class TemplateFillEngine {

    private final DataFormatter formatter = new DataFormatter();

    /**
     * 回填模板。
     *
     * @param template 模板文件字节
     * @param fileName 原始文件名（据此判断 xlsx / docx）
     * @param values   字段名 → 待填值（null 值会被跳过）
     * @return 回填后的字节与写入明细
     */
    public FillOutcome fill(byte[] template, String fileName, Map<String, String> values) throws IOException {
        String ext = extensionOf(fileName);
        if ("xlsx".equals(ext)) {
            return fillXlsx(template, fileName, values);
        }
        if ("docx".equals(ext)) {
            return fillDocx(template, fileName, values);
        }
        throw new IllegalArgumentException("仅支持 xlsx / docx 模板，当前文件类型: " + ext);
    }

    // ==================== 表头识别 ====================

    /**
     * 识别模板表头字段（供抽取与前端预览使用）。
     *
     * @return 表头文本列表；无法识别时返回空列表
     */
    public List<String> detectHeaders(byte[] template, String fileName) throws IOException {
        String ext = extensionOf(fileName);
        if ("xlsx".equals(ext)) {
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template))) {
                if (wb.getNumberOfSheets() == 0) {
                    return new ArrayList<>();
                }
                Sheet sheet = wb.getSheetAt(0);
                int headerRowIdx = detectHeaderRow(sheet);
                if (headerRowIdx < 0) {
                    return new ArrayList<>();
                }
                return readExcelRow(sheet.getRow(headerRowIdx)).stream()
                        .filter(s -> !s.isBlank())
                        .toList();
            }
        }
        if ("docx".equals(ext)) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(template))) {
                for (XWPFTable table : doc.getTables()) {
                    List<XWPFTableRow> rows = table.getRows();
                    for (int r = 0; r < Math.min(3, rows.size()); r++) {
                        List<String> headers = readDocxRow(rows.get(r)).stream()
                                .filter(s -> !s.isBlank())
                                .toList();
                        if (headers.size() >= 2) {
                            return headers;
                        }
                    }
                }
                return new ArrayList<>();
            }
        }
        throw new IllegalArgumentException("仅支持 xlsx / docx 模板，当前文件类型: " + ext);
    }

    // ==================== Excel ====================

    private FillOutcome fillXlsx(byte[] template, String fileName, Map<String, String> values) throws IOException {
        List<FillOutcome.WriteRecord> records = new ArrayList<>();
        Set<String> written = new LinkedHashSet<>();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                int headerRowIdx = detectHeaderRow(sheet);
                if (headerRowIdx < 0) {
                    continue;
                }
                List<String> headers = readExcelRow(sheet.getRow(headerRowIdx));
                Map<String, Integer> matched = FieldMatcher.matchAll(new ArrayList<>(values.keySet()), headers);
                if (matched.isEmpty()) {
                    continue;
                }

                int dataRowIdx = findFillRow(sheet, headerRowIdx, matched.values());
                Row dataRow = sheet.getRow(dataRowIdx);
                if (dataRow == null) {
                    dataRow = sheet.createRow(dataRowIdx);
                    inheritStyleFromAbove(sheet, dataRowIdx, matched.values());
                }

                for (Map.Entry<String, Integer> entry : matched.entrySet()) {
                    String field = entry.getKey();
                    String value = values.get(field);
                    if (value == null || value.isBlank() || written.contains(field)) {
                        continue;
                    }
                    int col = entry.getValue();
                    int[] target = resolveMergedTarget(sheet, dataRowIdx, col);
                    Row targetRow = sheet.getRow(target[0]);
                    if (targetRow == null) {
                        targetRow = sheet.createRow(target[0]);
                    }
                    Cell cell = targetRow.getCell(target[1]);
                    if (cell == null) {
                        cell = targetRow.createCell(target[1]);
                        copyStyle(sheet, targetRow.getRowNum(), target[1], cell);
                    }
                    // 公式单元格不覆盖，避免破坏模板计算逻辑
                    if (cell.getCellType() == CellType.FORMULA) {
                        continue;
                    }
                    writeExcelValue(cell, value);
                    records.add(new FillOutcome.WriteRecord(
                            field, sheet.getSheetName() + "!" + cellRef(target[0], target[1]), value));
                    written.add(field);
                }
            }

            wb.write(out);
            List<String> unmatched = unmatchedFields(values, written);
            return new FillOutcome(out.toByteArray(), fileName, "xlsx", records.size(), records, unmatched);
        }
    }

    /**
     * 表头行：前 10 行中非空单元格最多的一行。
     */
    private int detectHeaderRow(Sheet sheet) {
        int first = sheet.getFirstRowNum();
        int last = Math.min(sheet.getLastRowNum(), first + 9);
        int bestRow = -1;
        int bestCount = 0;
        for (int r = first; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int count = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && !formatter.formatCellValue(cell).isBlank()) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestRow = r;
            }
        }
        return bestCount > 0 ? bestRow : -1;
    }

    private List<String> readExcelRow(Row row) {
        List<String> list = new ArrayList<>();
        if (row == null) {
            return list;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            list.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return list;
    }

    /**
     * 待填行：表头下方第一行「匹配列全为空」的行；若都已有值，则追加到末尾。
     */
    private int findFillRow(Sheet sheet, int headerRowIdx, Iterable<Integer> cols) {
        int last = sheet.getLastRowNum();
        for (int r = headerRowIdx + 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                return r;
            }
            boolean allEmpty = true;
            for (int col : cols) {
                Cell cell = row.getCell(col);
                if (cell != null && !formatter.formatCellValue(cell).isBlank()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                return r;
            }
        }
        return last + 1;
    }

    /**
     * 合并单元格：写入区域左上角，避免写到被合并覆盖的单元格。
     */
    private int[] resolveMergedTarget(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                return new int[]{region.getFirstRow(), region.getFirstColumn()};
            }
        }
        return new int[]{rowIdx, colIdx};
    }

    /**
     * 新建行时复制上一行样式，保持表框线与字体一致。
     */
    private void inheritStyleFromAbove(Sheet sheet, int rowIdx, Iterable<Integer> cols) {
        Row above = sheet.getRow(rowIdx - 1);
        Row target = sheet.getRow(rowIdx);
        if (above == null || target == null) {
            return;
        }
        for (int col : cols) {
            Cell src = above.getCell(col);
            if (src == null) {
                continue;
            }
            Cell dst = target.createCell(col);
            dst.setCellStyle(src.getCellStyle());
        }
    }

    private void copyStyle(Sheet sheet, int rowIdx, int colIdx, Cell target) {
        // 首列没有左侧单元格可参照，保留默认样式
        if (colIdx <= 0) {
            return;
        }
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return;
        }
        Cell left = row.getCell(colIdx - 1);
        if (left != null) {
            target.setCellStyle(left.getCellStyle());
        }
    }

    /**
     * 写值：纯数字写数值型（保留数字格式），否则写字符串。
     */
    private void writeExcelValue(Cell cell, String value) {
        Double number = tryParseNumber(value);
        CellType type = cell.getCellType();
        boolean numericCell = type == CellType.NUMERIC || type == CellType.BLANK
                || type == CellType._NONE || type == CellType.STRING;
        if (number != null && numericCell) {
            cell.setCellValue(number);
        } else {
            cell.setCellValue(value);
        }
    }

    private Double tryParseNumber(String value) {
        String s = value.trim().replace(",", "").replace("，", "");
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String cellRef(int rowIdx, int colIdx) {
        StringBuilder sb = new StringBuilder();
        int n = colIdx;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        }
        return sb.append(rowIdx + 1).toString();
    }

    // ==================== Word ====================

    private FillOutcome fillDocx(byte[] template, String fileName, Map<String, String> values) throws IOException {
        List<FillOutcome.WriteRecord> records = new ArrayList<>();
        Set<String> written = new LinkedHashSet<>();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int tableIdx = 0;
            for (XWPFTable table : doc.getTables()) {
                tableIdx++;
                List<XWPFTableRow> rows = table.getRows();
                if (rows.isEmpty()) {
                    continue;
                }

                // 在表格前 3 行里找匹配字段最多的行作为表头
                int headerIdx = -1;
                Map<String, Integer> bestMatch = new LinkedHashMap<>();
                for (int r = 0; r < Math.min(3, rows.size()); r++) {
                    List<String> headers = readDocxRow(rows.get(r));
                    Map<String, Integer> matched =
                            FieldMatcher.matchAll(new ArrayList<>(values.keySet()), headers);
                    if (matched.size() > bestMatch.size()) {
                        bestMatch = matched;
                        headerIdx = r;
                    }
                }
                if (headerIdx < 0 || bestMatch.isEmpty()) {
                    continue;
                }

                int dataIdx = headerIdx + 1;
                XWPFTableRow dataRow = dataIdx < rows.size() ? rows.get(dataIdx) : table.createRow();
                List<XWPFTableCell> cells = dataRow.getTableCells();

                for (Map.Entry<String, Integer> entry : bestMatch.entrySet()) {
                    String field = entry.getKey();
                    String value = values.get(field);
                    if (value == null || value.isBlank() || written.contains(field)) {
                        continue;
                    }
                    int col = entry.getValue();
                    if (col >= cells.size()) {
                        continue;
                    }
                    setCellText(cells.get(col), value);
                    records.add(new FillOutcome.WriteRecord(
                            field, "table" + tableIdx + "[r" + dataIdx + ",c" + col + "]", value));
                    written.add(field);
                }
            }

            doc.write(out);
            List<String> unmatched = unmatchedFields(values, written);
            return new FillOutcome(out.toByteArray(), fileName, "docx", records.size(), records, unmatched);
        }
    }

    private List<String> readDocxRow(XWPFTableRow row) {
        List<String> list = new ArrayList<>();
        for (XWPFTableCell cell : row.getTableCells()) {
            list.add(cell.getText() == null ? "" : cell.getText().trim());
        }
        return list;
    }

    /**
     * 写入单元格文本，保留第一个 run 的字体格式，清空多余 run。
     */
    private void setCellText(XWPFTableCell cell, String value) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        XWPFParagraph paragraph = paragraphs.isEmpty() ? cell.addParagraph() : paragraphs.get(0);
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            paragraph.createRun().setText(value);
            return;
        }
        runs.get(0).setText(value, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    // ==================== 公共 ====================

    private List<String> unmatchedFields(Map<String, String> values, Set<String> written) {
        List<String> unmatched = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!written.contains(e.getKey()) && e.getValue() != null && !e.getValue().isBlank()) {
                unmatched.add(e.getKey());
            }
        }
        return unmatched;
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}

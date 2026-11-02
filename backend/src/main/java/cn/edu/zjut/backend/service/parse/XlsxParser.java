package cn.edu.zjut.backend.service.parse;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.dto.DocParseResult.Chunk;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xlsx 解析器：每个 sheet 生成一个 table chunk，保留单元格坐标与类型标注。
 */
@Component
public class XlsxParser implements DocParser {

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }

    @Override
    public DocParseResult parse(byte[] bytes, String fileName) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        int seq = 0;

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                List<List<String>> data = new ArrayList<>();
                List<String> typeAnnot = new ArrayList<>();
                int maxCol = 0;
                int maxRow = 0;

                for (Row row : sheet) {
                    List<String> rowData = new ArrayList<>();
                    int rowIdx = row.getRowNum();
                    maxRow = Math.max(maxRow, rowIdx + 1);
                    int lastCell = row.getLastCellNum();
                    maxCol = Math.max(maxCol, lastCell);
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        rowData.add(formatCell(cell));
                    }
                    data.add(rowData);
                }

                // 类型标注：首行（表头）之外，抽样记录数字/日期类型
                if (!data.isEmpty()) {
                    typeAnnot.add("header");
                    for (int i = 1; i < data.size(); i++) {
                        typeAnnot.add("row" + (i + 1));
                    }
                }

                String cellRef = sheetName + "!A1:" + colName(Math.max(maxCol, 1)) + maxRow;
                String content = serializeTable(data, sheetName);
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("sheet", sheetName);
                meta.put("rows", String.valueOf(maxRow));
                meta.put("cols", String.valueOf(maxCol));
                chunks.add(new Chunk(seq++, "table", sheetName, content, cellRef, data, meta));
            }
        }
        return new DocParseResult(fileName, "xlsx", chunks.size(), chunks);
    }

    private String formatCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return new DataFormatter().formatCellValue(cell);
            }
            // 数字：避免科学计数法，保留原值
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.FORMULA) {
            return cell.getCellFormula();
        }
        return cell.getStringCellValue();
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

    private String serializeTable(List<List<String>> data, String sheetName) {
        StringBuilder sb = new StringBuilder("[sheet: ").append(sheetName).append("]\n");
        for (List<String> row : data) {
            sb.append(String.join(" | ", row)).append("\n");
        }
        return sb.toString().trim();
    }
}

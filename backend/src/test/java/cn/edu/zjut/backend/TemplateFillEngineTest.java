package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.FillOutcome;
import cn.edu.zjut.backend.service.fill.TemplateFillEngine;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板保真回填引擎单元测试（纯 POI，无 Spring / 数据库 / 外部服务依赖）。
 * 验证：值写入正确、表头样式与合并单元格未被破坏、公式不被覆盖、未匹配字段被记录。
 */
class TemplateFillEngineTest {

    private final TemplateFillEngine engine = new TemplateFillEngine();

    @Test
    void fillXlsxWritesValuesAndKeepsStyle() throws Exception {
        byte[] template = buildXlsxTemplate();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("公司名称", "某科技有限公司");
        values.put("注册资本", "1000");
        values.put("成立年份", "2020");

        FillOutcome outcome = engine.fill(template, "template.xlsx", values);

        assertThat(outcome.format()).isEqualTo("xlsx");
        assertThat(outcome.filledCount()).isEqualTo(3);
        assertThat(outcome.unmatchedFields()).isEmpty();
        assertThat(outcome.records())
                .extracting(FillOutcome.WriteRecord::targetRef)
                .contains("Sheet1!A3", "Sheet1!B3", "Sheet1!C3");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(outcome.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            // 值正确写入（数字保持数值型，便于后续计算与比对）
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("某科技有限公司");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(2020.0);
            // 表头样式保留（加粗）
            CellStyle headerStyle = sheet.getRow(1).getCell(0).getCellStyle();
            assertThat(wb.getFontAt(headerStyle.getFontIndexAsInt()).getBold()).isTrue();
            // 标题合并单元格与文本未被破坏
            assertThat(sheet.getMergedRegions()).anyMatch(r -> r.getFirstRow() == 0 && r.getLastRow() == 0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("公司信息表");
        }
    }

    @Test
    void fillXlsxSkipsFormulaCells() throws Exception {
        byte[] template = buildXlsxTemplateWithFormula();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("合计", "999");

        FillOutcome outcome = engine.fill(template, "formula.xlsx", values);

        // 公式所在行视为已有内容，值写到其后的新行，公式本身不被覆盖
        assertThat(outcome.records())
                .extracting(FillOutcome.WriteRecord::targetRef)
                .containsExactly("Sheet1!A3");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(outcome.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);
            assertThat(sheet.getRow(1).getCell(0).getCellFormula()).isEqualTo("1+1");
            assertThat(sheet.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(999.0);
        }
    }

    @Test
    void fillDocxWritesValuesAndKeepsHeaderStyle() throws Exception {
        byte[] template = buildDocxTemplate();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("项目", "注册资本");
        values.put("数值", "1000");

        FillOutcome outcome = engine.fill(template, "template.docx", values);

        assertThat(outcome.format()).isEqualTo("docx");
        assertThat(outcome.filledCount()).isEqualTo(2);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(outcome.content()))) {
            XWPFTable table = doc.getTables().get(0);
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("注册资本");
            assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("1000");
            // 表头加粗未被破坏
            XWPFRun run = table.getRow(0).getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertThat(run.isBold()).isTrue();
        }
    }

    @Test
    void unmatchedFieldIsReported() throws Exception {
        byte[] template = buildXlsxTemplate();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("联系电话", "0571-12345678");

        FillOutcome outcome = engine.fill(template, "template.xlsx", values);

        assertThat(outcome.filledCount()).isZero();
        assertThat(outcome.unmatchedFields()).containsExactly("联系电话");
    }

    @Test
    void unsupportedFormatIsRejected() {
        assertThatThrownBy(() -> engine.fill(new byte[0], "a.pdf", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xlsx / docx");
    }

    @Test
    void detectHeadersFindsHeaderRowNotTitleRow() throws Exception {
        byte[] template = buildXlsxTemplate();
        assertThat(engine.detectHeaders(template, "template.xlsx"))
                .containsExactly("公司名称", "注册资本（万元）", "成立年份");
    }

    // ==================== 测试夹具 ====================

    private byte[] buildXlsxTemplate() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");

            // 第 1 行：标题（合并 A1:C1）
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("公司信息表");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            // 第 2 行：表头（加粗）
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(1);
            String[] headers = {"公司名称", "注册资本（万元）", "成立年份"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(bold);
            }

            // 第 3 行：待填空行
            sheet.createRow(2);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsxTemplateWithFormula() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("合计");
            Row data = sheet.createRow(1);
            Cell formula = data.createCell(0);
            formula.setCellFormula("1+1");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxTemplate() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable(3, 3);
            String[] headers = {"项目", "数值", "单位"};
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < headers.length; i++) {
                XWPFTableCell cell = headerRow.getCell(i);
                cell.setText(headers[i]);
                cell.getParagraphs().get(0).getRuns().get(0).setBold(true);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }
}

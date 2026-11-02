package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.service.ParseService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档解析器集成测试：生成 docx/xlsx 样本并验证解析结果。
 * 同时把样本文件写到 D:/WorkBuddy/tmp/test/ 供手动联调。
 */
@SpringBootTest
class ParseServiceTest {

    @Autowired
    private ParseService parseService;

    @Test
    void parseDocx() throws Exception {
        byte[] docx = buildDocx();
        DocParseResult result = parseService.parse(docx, "test.docx");

        assertThat(result.fileType()).isEqualTo("docx");
        assertThat(result.chunkCount()).isGreaterThan(0);
        // 至少有一个 text chunk 和 table chunk
        assertThat(result.chunks()).anyMatch(c -> "text".equals(c.type()));
        assertThat(result.chunks()).anyMatch(c -> "table".equals(c.type()));

        save(docx, "test.docx");
    }

    @Test
    void parseXlsx() throws Exception {
        byte[] xlsx = buildXlsx();
        DocParseResult result = parseService.parse(xlsx, "test.xlsx");

        assertThat(result.fileType()).isEqualTo("xlsx");
        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(result.chunks()).allMatch(c -> "table".equals(c.type()));

        save(xlsx, "test.xlsx");
    }

    private byte[] buildDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 标题 + 正文
            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading1");
            h1.createRun().setText("公司简介");

            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("某科技有限公司成立于2020年，注册资本1000万元。");

            // 表格
            XWPFTable table = doc.createTable(3, 3);
            String[][] data = {
                    {"项目", "数值", "单位"},
                    {"注册资本", "1000", "万元"},
                    {"成立年份", "2020", "年"}
            };
            int r = 0;
            for (String[] rowData : data) {
                XWPFTableRow row = table.getRow(r);
                for (int c = 0; c < rowData.length; c++) {
                    XWPFTableCell cell = row.getCell(c);
                    cell.setText(rowData[c]);
                }
                r++;
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("公司信息");
            String[][] data = {
                    {"公司名称", "成立年份", "注册资本(万元)"},
                    {"某科技", "2020", "1000"},
                    {"某贸易", "2018", "500"}
            };
            int r = 0;
            for (String[] rowData : data) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < rowData.length; c++) {
                    row.createCell(c).setCellValue(rowData[c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void save(byte[] data, String name) throws Exception {
        Path dir = Path.of("D:/WorkBuddy/tmp/test");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), data);
    }
}

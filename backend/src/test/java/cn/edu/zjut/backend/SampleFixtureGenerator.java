package cn.edu.zjut.backend;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 联调样本生成器：产出 docx / xlsx / md / txt 样本与模板，供本地接口验证使用。
 * 生成目录：D:/WorkBuddy/tmp/samples
 */
class SampleFixtureGenerator {

    private static final Path DIR = Path.of("D:/WorkBuddy/tmp/samples");

    @Test
    void generateSamples() throws Exception {
        Files.createDirectories(DIR);

        String companyText = "公司名称：某科技有限公司\n"
                + "注册资本1000万元，成立于2020年1月1日，注册地址为杭州市西湖区。\n"
                + "统一社会信用代码：91330100MA2XXXXX0A。\n"
                + "公司主营软件开发与技术服务，2023年营业收入8600万元。\n";

        // 1. docx 样本（标题 + 正文 + 表格）
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph h = doc.createParagraph();
            h.setStyle("Heading1");
            h.createRun().setText("公司基本信息");
            for (String line : companyText.split("\n")) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            XWPFTable table = doc.createTable(3, 3);
            String[][] rows = {
                    {"项目", "数值", "单位"},
                    {"注册资本", "1000", "万元"},
                    {"成立年份", "2020", "年"}
            };
            for (int r = 0; r < rows.length; r++) {
                XWPFTableRow row = table.getRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    XWPFTableCell cell = row.getCell(c);
                    cell.setText(rows[r][c]);
                }
            }
            doc.write(out);
            Files.write(DIR.resolve("sample-company.docx"), out.toByteArray());
        }

        // 2. md 样本
        String md = "# 项目说明\n\n## 建设目标\n\n构建文档理解与多源数据融合系统。\n\n"
                + "## 关键指标\n\n准确率需达到80%以上，单表响应时间不超过90秒。\n";
        Files.write(DIR.resolve("sample-notes.md"), md.getBytes(StandardCharsets.UTF_8));

        // 3. txt 样本
        Files.write(DIR.resolve("sample-notes.txt"),
                ("客户名称：某贸易有限公司\n合同金额500万元，签订日期2021年6月30日。\n")
                        .getBytes(StandardCharsets.UTF_8));

        // 4. Excel 模板（表头 + 待填空行）
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("公司信息");
            Row header = sheet.createRow(0);
            String[] headers = {"公司名称", "注册资本（万元）", "成立年份"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }
            sheet.createRow(1);
            wb.write(out);
            Files.write(DIR.resolve("template.xlsx"), out.toByteArray());
        }

        // 5. Word 模板（表格表头 + 空数据行）
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("登记表");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("公司名称");
            table.getRow(0).getCell(1).setText("注册资本");
            doc.write(out);
            Files.write(DIR.resolve("template.docx"), out.toByteArray());
        }

        // 6. 文档操作目标文件
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph h = doc.createParagraph();
            h.setStyle("Heading1");
            h.createRun().setText("合作备忘录");
            doc.createParagraph().createRun().setText("甲方：某科技有限公司");
            doc.createParagraph().createRun().setText("乙方：某贸易有限公司");
            doc.createParagraph().createRun().setText("合同金额为500万元。");
            doc.write(out);
            Files.write(DIR.resolve("edit-target.docx"), out.toByteArray());
        }

        System.out.println("样本已生成到 " + DIR.toAbsolutePath());
    }
}

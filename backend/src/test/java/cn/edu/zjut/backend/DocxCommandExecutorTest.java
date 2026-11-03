package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.DocCommand;
import cn.edu.zjut.backend.dto.ExecutionReport;
import cn.edu.zjut.backend.service.docop.DocxCommandExecutor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docx 指令执行器单元测试：验证动作生效、范围准确、越界安全。
 */
class DocxCommandExecutorTest {

    private final DocxCommandExecutor executor = new DocxCommandExecutor();

    @Test
    void boldAllParagraphs() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(DocCommand.of("set_bold", "all", "true")));

        assertThat(report.affected()).isGreaterThan(0);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            assertThat(doc.getParagraphs())
                    .allMatch(p -> p.getRuns().stream().allMatch(XWPFRun::isBold));
        }
    }

    @Test
    void boldOnlyHeadings() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(DocCommand.of("set_bold", "headings", "true")));

        assertThat(report.affected()).isEqualTo(1);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            assertThat(doc.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(doc.getParagraphs().get(1).getRuns().get(0).isBold()).isFalse();
        }
    }

    @Test
    void fontSizeAppliedToSpecificParagraph() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(DocCommand.of("set_font_size", "paragraph:2", "12")));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            assertThat(doc.getParagraphs().get(1).getRuns().get(0).getFontSize()).isEqualTo(12);
            assertThat(doc.getParagraphs().get(2).getRuns().get(0).getFontSize()).isNotEqualTo(12);
        }
    }

    @Test
    void replaceTextKeepsOtherContent() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(new DocCommand("replace_text", "all", "甲方", "乙方")));

        assertThat(report.affected()).isEqualTo(1);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            List<String> texts = doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();
            assertThat(texts).anyMatch(t -> t.contains("乙方"));
            assertThat(texts).noneMatch(t -> t.contains("甲方"));
        }
    }

    @Test
    void alignFirstParagraph() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(DocCommand.of("set_align", "first_paragraph", "center")));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            assertThat(doc.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
        }
    }

    @Test
    void outOfRangeScopeIsSafe() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(DocCommand.of("set_bold", "paragraph:99", "true")));

        assertThat(report.affected()).isZero();
        // 文档仍可正常打开，未被破坏
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(report.content()))) {
            assertThat(doc.getParagraphs()).isNotEmpty();
        }
    }

    @Test
    void missingReplaceTargetReportsZero() throws Exception {
        ExecutionReport report = executor.execute(buildDocx(),
                List.of(new DocCommand("replace_text", "all", "不存在的词", "X")));

        assertThat(report.affected()).isZero();
        assertThat(report.changes().get(0).detail()).contains("未找到");
    }

    private byte[] buildDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("合同标题");

            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("甲方：某科技有限公司");

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("注册资本为1000万元");

            doc.write(out);
            return out.toByteArray();
        }
    }
}

package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.DocCommand;
import cn.edu.zjut.backend.dto.DocIntent;
import cn.edu.zjut.backend.service.docop.DocIntentParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自然语言指令解析单元测试（规则版，无 LLM 依赖）。
 */
class DocIntentParserTest {

    private final DocIntentParser parser = new DocIntentParser();

    @Test
    void boldWholeDocument() {
        DocIntent intent = parser.parse("把全文加粗");
        assertThat(intent.intent()).isEqualTo("FORMAT");
        assertThat(intent.commands()).hasSize(1);
        DocCommand cmd = intent.commands().get(0);
        assertThat(cmd.action()).isEqualTo("set_bold");
        assertThat(cmd.scope()).isEqualTo("all");
        assertThat(cmd.value()).isEqualTo("true");
    }

    @Test
    void boldAllHeadings() {
        DocIntent intent = parser.parse("所有标题加粗");
        assertThat(intent.commands().get(0).scope()).isEqualTo("headings");
    }

    @Test
    void specificParagraphWithChineseFontSize() {
        DocIntent intent = parser.parse("第2段字号改为小四");
        DocCommand cmd = intent.commands().get(0);
        assertThat(cmd.scope()).isEqualTo("paragraph:2");
        assertThat(cmd.action()).isEqualTo("set_font_size");
        assertThat(cmd.value()).isEqualTo("12");
    }

    @Test
    void numericFontSizeInPoints() {
        DocIntent intent = parser.parse("第二段字号设为15磅");
        assertThat(intent.commands().get(0).value()).isEqualTo("15");
    }

    @Test
    void centerAlignment() {
        DocIntent intent = parser.parse("所有段落居中");
        assertThat(intent.intent()).isEqualTo("TYPESET");
        assertThat(intent.commands().get(0).action()).isEqualTo("set_align");
        assertThat(intent.commands().get(0).value()).isEqualTo("center");
    }

    @Test
    void replaceText() {
        DocIntent intent = parser.parse("把甲方替换为乙方");
        assertThat(intent.intent()).isEqualTo("EDIT");
        DocCommand cmd = intent.commands().get(0);
        assertThat(cmd.action()).isEqualTo("replace_text");
        assertThat(cmd.value()).isEqualTo("甲方");
        assertThat(cmd.replaceTo()).isEqualTo("乙方");
    }

    @Test
    void changeFontNameIsNotMisparsedAsReplace() {
        DocIntent intent = parser.parse("把字体改为宋体");
        assertThat(intent.intent()).isEqualTo("FORMAT");
        assertThat(intent.commands()).hasSize(1);
        assertThat(intent.commands().get(0).action()).isEqualTo("set_font_name");
        assertThat(intent.commands().get(0).value()).isEqualTo("宋体");
    }

    @Test
    void multipleActionsInOneInstruction() {
        DocIntent intent = parser.parse("全文加粗并居中");
        assertThat(intent.commands()).hasSize(2);
        assertThat(intent.commands()).extracting(DocCommand::action)
                .containsExactly("set_bold", "set_align");
    }

    @Test
    void extractIntentIsNotExecuted() {
        DocIntent intent = parser.parse("提取合同金额");
        assertThat(intent.intent()).isEqualTo("EXTRACT");
        assertThat(intent.commands()).isEmpty();
        assertThat(intent.message()).contains("/api/extract");
    }

    @Test
    void tableStructureChangeIsBlocked() {
        DocIntent intent = parser.parse("在表格中插入一行");
        assertThat(intent.intent()).isEqualTo("TABLE");
        assertThat(intent.commands()).isEmpty();
    }

    @Test
    void dangerousActionIsRejected() {
        DocIntent intent = parser.parse("删除所有内容");
        assertThat(intent.intent()).isEqualTo("UNKNOWN");
        assertThat(intent.commands()).isEmpty();
        assertThat(intent.message()).contains("白名单");
    }

    @Test
    void unknownInstructionIsReported() {
        DocIntent intent = parser.parse("帮我看看这个文档怎么样");
        assertThat(intent.intent()).isEqualTo("UNKNOWN");
        assertThat(intent.commands()).isEmpty();
        assertThat(intent.message()).contains("未能识别");
    }

    @Test
    void blankInstruction() {
        DocIntent intent = parser.parse("   ");
        assertThat(intent.intent()).isEqualTo("UNKNOWN");
    }
}

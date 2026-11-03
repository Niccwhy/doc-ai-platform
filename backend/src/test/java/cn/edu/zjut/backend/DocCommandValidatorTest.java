package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.DocCommand;
import cn.edu.zjut.backend.service.docop.DocCommandValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指令白名单校验单元测试。
 */
class DocCommandValidatorTest {

    private final DocCommandValidator validator = new DocCommandValidator();

    @Test
    void validCommandsPass() {
        List<String> errors = validator.validate(List.of(
                DocCommand.of("set_bold", "all", "true"),
                DocCommand.of("set_font_size", "paragraph:2", "12"),
                DocCommand.of("set_align", "headings", "center"),
                new DocCommand("replace_text", "all", "甲方", "乙方")));
        assertThat(errors).isEmpty();
    }

    @Test
    void unknownActionIsRejected() {
        List<String> errors = validator.validate(List.of(
                DocCommand.of("delete_file", "all", "true")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("白名单");
    }

    @Test
    void illegalScopeIsRejected() {
        List<String> errors = validator.validate(List.of(
                DocCommand.of("set_bold", "everything", "true")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("作用范围");
    }

    @Test
    void outOfRangeFontSizeIsRejected() {
        assertThat(validator.validate(List.of(DocCommand.of("set_font_size", "all", "200"))))
                .isNotEmpty();
    }

    @Test
    void nonNumericFontSizeIsRejected() {
        assertThat(validator.validate(List.of(DocCommand.of("set_font_size", "all", "很大"))))
                .isNotEmpty();
    }

    @Test
    void illegalAlignmentIsRejected() {
        assertThat(validator.validate(List.of(DocCommand.of("set_align", "all", "justify-all"))))
                .isNotEmpty();
    }

    @Test
    void emptyReplaceTargetIsRejected() {
        assertThat(validator.validate(List.of(
                new DocCommand("replace_text", "all", "甲方", " ")))).isNotEmpty();
    }

    @Test
    void emptyCommandListIsRejected() {
        assertThat(validator.validate(List.of())).hasSize(1);
    }
}

package cn.edu.zjut.backend;

import cn.edu.zjut.backend.service.fill.FieldMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表头匹配器单元测试（纯逻辑，无 Spring 依赖）。
 */
class FieldMatcherTest {

    @Test
    void matchExact() {
        List<String> headers = List.of("公司名称", "注册资本", "成立年份");
        assertThat(FieldMatcher.match("注册资本", headers)).isEqualTo(1);
    }

    @Test
    void matchWithUnitInHeader() {
        List<String> headers = List.of("公司名称", "注册资本（万元）", "成立年份");
        assertThat(FieldMatcher.match("注册资本", headers)).isEqualTo(1);
    }

    @Test
    void matchWithHalfWidthBracketAndSpaces() {
        List<String> headers = List.of(" 公司 名称 ", "注册资本(万元)");
        assertThat(FieldMatcher.match("公司名称", headers)).isEqualTo(0);
        assertThat(FieldMatcher.match("注册资本", headers)).isEqualTo(1);
    }

    @Test
    void matchByContains() {
        List<String> headers = List.of("合同金额合计说明", "金额");
        // 归一化后既有完全相等项，应优先精确命中
        assertThat(FieldMatcher.match("金额", headers)).isEqualTo(1);
    }

    @Test
    void matchByContainsWhenNoExact() {
        List<String> headers = List.of("甲方单位名称（盖章）");
        assertThat(FieldMatcher.match("甲方", headers)).isEqualTo(0);
    }

    @Test
    void unmatchedReturnsMinusOne() {
        List<String> headers = List.of("公司名称", "成立年份");
        assertThat(FieldMatcher.match("联系电话", headers)).isEqualTo(-1);
    }

    @Test
    void matchAllSkipsUnmatched() {
        List<String> headers = List.of("公司名称", "成立年份");
        var result = FieldMatcher.matchAll(List.of("公司名称", "联系电话"), headers);
        assertThat(result).containsKey("公司名称").doesNotContainKey("联系电话");
    }

    @Test
    void normalizeRemovesBracketContentAndPunctuation() {
        assertThat(FieldMatcher.normalize("注册资本（万元）")).isEqualTo("注册资本");
        assertThat(FieldMatcher.normalize("金额：人民币")).isEqualTo("金额人民币");
    }
}

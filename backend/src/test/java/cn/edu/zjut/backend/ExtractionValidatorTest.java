package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.ValidationResult;
import cn.edu.zjut.backend.service.extract.ExtractionValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽取校验器单元测试（纯逻辑）。
 * 覆盖：数字/日期/布尔归一、千分位与单位、编造值拦截、空值与非法值。
 */
class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator();

    @Test
    void numberWithUnitAndThousandSeparatorIsNormalizedAndGrounded() {
        ValidationResult r = validator.validate("1,000万元", "注册资本 1,000 万元", "number", "万元");
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("1000");
        assertThat(r.confidence()).isEqualTo(1.0);
    }

    @Test
    void numberTrailingZerosAreCanonicalized() {
        ValidationResult r = validator.validate("1000.00", "注册资本 1000 万元", "number", "万元");
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("1000");
    }

    @Test
    void fabricatedNumberIsRejected() {
        ValidationResult r = validator.validate("5000", "注册资本1000万元", "number", "万元");
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("疑似编造");
    }

    @Test
    void nonNumericValueForNumberFieldIsRejected() {
        ValidationResult r = validator.validate("约一千", "约一千万元", "number", "万元");
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("不符合字段类型");
    }

    @Test
    void chineseDateIsNormalizedToIso() {
        ValidationResult r = validator.validate("2020年1月1日", "公司成立于2020年1月1日", "date", null);
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("2020-01-01");
    }

    @Test
    void slashDateIsNormalizedToIso() {
        ValidationResult r = validator.validate("2020/1/5", "2020年1月5日签订合同", "date", null);
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("2020-01-05");
    }

    @Test
    void invalidDateIsRejected() {
        ValidationResult r = validator.validate("2020年13月45日", "2020年13月45日", "date", null);
        assertThat(r.accepted()).isFalse();
    }

    @Test
    void booleanTrueIsNormalized() {
        ValidationResult r = validator.validate("是", "是否为高新技术企业：是", "boolean", null);
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("true");
    }

    @Test
    void booleanFalseIsNormalized() {
        ValidationResult r = validator.validate("否", "是否上市：否", "boolean", null);
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("false");
    }

    @Test
    void stringValueIsGroundedAfterCompaction() {
        ValidationResult r = validator.validate(" 某  科技 有限公司 ", "甲方：某科技有限公司", "string", null);
        assertThat(r.accepted()).isTrue();
        assertThat(r.normalizedValue()).isEqualTo("某 科技 有限公司");
    }

    @Test
    void blankValueIsRejected() {
        ValidationResult r = validator.validate("   ", "任意证据", "string", null);
        assertThat(r.accepted()).isFalse();
        assertThat(r.confidence()).isZero();
        assertThat(r.reason()).contains("值为空");
    }

    @Test
    void missingEvidenceIsRejected() {
        ValidationResult r = validator.validate("1000", "", "number", "万元");
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("证据");
    }
}

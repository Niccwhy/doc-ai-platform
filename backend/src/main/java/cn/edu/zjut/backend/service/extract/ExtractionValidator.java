package cn.edu.zjut.backend.service.extract;

import cn.edu.zjut.backend.dto.ValidationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抽取结果校验器（纯逻辑，无外部依赖）。
 * <p>
 * 三项加权：① 值是否可解析为声明类型；② 引文（取值的原文依据）是否能在证据片段中找到；
 * ③ 归一化后是否非空。只有「类型有效 + 有原文依据」才判定为可采信，
 * 用于压制模型编造，支撑幻觉率 < 2% 的指标口径。
 */
public class ExtractionValidator {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern DATE = Pattern.compile(
            "(\\d{4})\\s*[年/.\\-]\\s*(\\d{1,2})\\s*[月/.\\-]\\s*(\\d{1,2})\\s*日?");

    private static final Set<String> TRUE_WORDS = Set.of("是", "有", "true", "1", "y", "yes", "√", "已", "存在");
    private static final Set<String> FALSE_WORDS = Set.of("否", "无", "false", "0", "n", "no", "×", "未", "不存在");

    /**
     * 校验单个字段抽取结果。
     *
     * @param rawValue 模型返回的原始值
     * @param evidence 该值所依据的原文片段
     * @param dataType 字段类型：string / number / date / boolean
     * @param unit     单位（number 类型时用于去除，如「万元」）
     */
    public ValidationResult validate(String rawValue, String evidence, String dataType, String unit) {
        if (rawValue == null || rawValue.isBlank()) {
            return new ValidationResult(false, 0.0, null, "值为空");
        }

        String type = (dataType == null || dataType.isBlank()) ? "string" : dataType.toLowerCase();
        String normalized;
        boolean typeValid;
        try {
            normalized = normalize(rawValue, type, unit);
            typeValid = normalized != null && !normalized.isBlank();
        } catch (RuntimeException e) {
            normalized = rawValue.trim();
            typeValid = false;
        }

        boolean grounded = isGrounded(rawValue, normalized, evidence, type);
        double confidence = 0.5 + (grounded ? 0.3 : 0.0) + (typeValid ? 0.2 : 0.0);

        String reason;
        boolean accepted;
        if (!typeValid) {
            reason = "值不符合字段类型 " + type;
            accepted = false;
        } else if (!grounded) {
            reason = "值未能在证据原文中找到，疑似编造";
            accepted = false;
        } else {
            reason = "校验通过";
            accepted = true;
        }
        return new ValidationResult(accepted, round(confidence), normalized, reason);
    }

    // ==================== 归一化 ====================

    private String normalize(String raw, String type, String unit) {
        return switch (type) {
            case "number" -> normalizeNumber(raw, unit);
            case "date" -> normalizeDate(raw);
            case "boolean" -> normalizeBoolean(raw);
            default -> raw.trim().replaceAll("\\s+", " ");
        };
    }

    /**
     * 数字归一：去千分位、去单位、取数字部分、去尾随零。
     */
    private String normalizeNumber(String raw, String unit) {
        String s = raw.replace(",", "").replace("，", "").replace(" ", "");
        if (unit != null && !unit.isBlank()) {
            s = s.replace(unit.trim(), "");
        }
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) {
            return null;
        }
        return canonicalNumber(m.group());
    }

    /**
     * 日期归一：多种分隔符统一为 yyyy-MM-dd。
     */
    private String normalizeDate(String raw) {
        Matcher m = DATE.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
            return date.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String normalizeBoolean(String raw) {
        String s = raw.trim().toLowerCase();
        if (TRUE_WORDS.contains(s)) {
            return "true";
        }
        if (FALSE_WORDS.contains(s)) {
            return "false";
        }
        return null;
    }

    // ==================== 引文溯源 ====================

    /**
     * 判断取值是否能在证据原文中找到依据（按类型采用不同比对方式）。
     */
    private boolean isGrounded(String raw, String normalized, String evidence, String type) {
        if (normalized == null || normalized.isBlank() || evidence == null || evidence.isBlank()) {
            return false;
        }
        return switch (type) {
            case "number" -> numbersIn(evidence).contains(canonicalNumber(normalized));
            case "date" -> datesIn(evidence).contains(normalized);
            case "boolean" -> compact(evidence).contains(compact(raw));
            default -> compact(evidence).contains(compact(normalized));
        };
    }

    private Set<String> numbersIn(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text.replace(",", "").replace("，", ""));
        while (m.find()) {
            result.add(canonicalNumber(m.group()));
        }
        return result;
    }

    private Set<String> datesIn(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            try {
                result.add(LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))).toString());
            } catch (RuntimeException ignored) {
                // 非法日期忽略
            }
        }
        return result;
    }

    /**
     * 去掉空白与标点，便于「原文包含」判断。
     */
    private String compact(String s) {
        return s.replaceAll("[\\s:：,，.。;；、\\-_/|()（）\\[\\]【】\"'“”]", "").toLowerCase();
    }

    /**
     * 数字规范化：BigDecimal 去尾零，避免 1000 与 1000.00 判不相等。
     */
    private String canonicalNumber(String num) {
        try {
            return new BigDecimal(num).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return num;
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

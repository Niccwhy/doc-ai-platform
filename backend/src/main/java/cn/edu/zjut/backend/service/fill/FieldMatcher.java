package cn.edu.zjut.backend.service.fill;

import java.util.List;
import java.util.Map;

/**
 * 模板表头与抽取字段的匹配器（纯逻辑，无外部依赖）。
 * <p>
 * 匹配顺序（由严到宽）：精确 → 归一化后相等 → 互相包含。
 * 归一化会去掉空白、标点、括号及括号内单位，例如「注册资本（万元）」→「注册资本」。
 */
public final class FieldMatcher {

    private FieldMatcher() {
    }

    /**
     * 在模板表头列表中为给定字段寻找匹配列。
     *
     * @return 匹配到的表头下标，找不到返回 -1
     */
    public static int match(String field, List<String> headers) {
        if (field == null || headers == null || headers.isEmpty()) {
            return -1;
        }
        // 1. 精确匹配
        for (int i = 0; i < headers.size(); i++) {
            if (field.equals(headers.get(i))) {
                return i;
            }
        }
        // 2. 归一化后相等
        String normField = normalize(field);
        if (normField.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (normField.equals(normalize(headers.get(i)))) {
                return i;
            }
        }
        // 3. 互相包含（取最短的候选，避免"金额"误配"金额合计说明"）
        int best = -1;
        int bestLen = Integer.MAX_VALUE;
        for (int i = 0; i < headers.size(); i++) {
            String normHeader = normalize(headers.get(i));
            if (normHeader.isEmpty()) {
                continue;
            }
            if (normHeader.contains(normField) || normField.contains(normHeader)) {
                if (normHeader.length() < bestLen) {
                    best = i;
                    bestLen = normHeader.length();
                }
            }
        }
        return best;
    }

    /**
     * 归一化：全角转半角、去括号内容、去空白与标点、转小写。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = toHalfWidth(raw);
        // 去掉括号及括号内内容（单位、说明）
        s = s.replaceAll("[（(\\[【][^）)\\]】]*[）)\\]】]", "");
        // 去掉空白与常见标点
        s = s.replaceAll("[\\s:：,，.。;；、\\-_/|]", "");
        return s.toLowerCase();
    }

    /**
     * 全角字符转半角（仅处理可见字符区）。
     */
    private static String toHalfWidth(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\u3000') {
                chars[i] = ' ';
            } else if (chars[i] >= '\uFF01' && chars[i] <= '\uFF5E') {
                chars[i] = (char) (chars[i] - 0xFEE0);
            }
        }
        return new String(chars);
    }

    /**
     * 批量匹配：字段名 → 表头下标（未命中不放入结果）。
     */
    public static Map<String, Integer> matchAll(List<String> fields, List<String> headers) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (String field : fields) {
            int idx = match(field, headers);
            if (idx >= 0) {
                result.put(field, idx);
            }
        }
        return result;
    }
}

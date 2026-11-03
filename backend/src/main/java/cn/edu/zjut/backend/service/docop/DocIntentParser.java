package cn.edu.zjut.backend.service.docop;

import cn.edu.zjut.backend.dto.DocCommand;
import cn.edu.zjut.backend.dto.DocIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言指令解析器（规则版，不依赖大模型）。
 * <p>
 * 覆盖五类意图中的四类：格式调整（加粗/斜体/下划线/字号/字体）、
 * 排版（对齐）、编辑（文本替换）、以及识别但不执行的提取/表格类。
 * 规则解析保证无 Key 也能演示完整链路；后续可在此之上叠加模型解析做兜底。
 */
public class DocIntentParser {

    /** 中文号数 → 磅值 */
    private static final Map<String, String> FONT_SIZE_WORDS = new LinkedHashMap<>();

    static {
        FONT_SIZE_WORDS.put("初号", "42");
        FONT_SIZE_WORDS.put("小初", "36");
        FONT_SIZE_WORDS.put("一号", "26");
        FONT_SIZE_WORDS.put("小一", "24");
        FONT_SIZE_WORDS.put("二号", "22");
        FONT_SIZE_WORDS.put("小二", "18");
        FONT_SIZE_WORDS.put("三号", "16");
        FONT_SIZE_WORDS.put("小三", "15");
        FONT_SIZE_WORDS.put("四号", "14");
        FONT_SIZE_WORDS.put("小四", "12");
        FONT_SIZE_WORDS.put("五号", "10.5");
        FONT_SIZE_WORDS.put("小五", "9");
        FONT_SIZE_WORDS.put("六号", "7.5");
        FONT_SIZE_WORDS.put("小六", "6.5");
    }

    private static final List<String> FONT_NAMES = List.of(
            "宋体", "黑体", "仿宋", "楷体", "微软雅黑", "等线", "华文中宋", "方正小标宋");

    private static final Pattern PARAGRAPH = Pattern.compile("第\\s*([0-9一二三四五六七八九十]+)\\s*段");
    private static final Pattern TABLE_INDEX = Pattern.compile("第\\s*([0-9一二三四五六七八九十]+)\\s*(?:个)?表格");
    private static final Pattern NUMERIC_SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:磅|pt)", Pattern.CASE_INSENSITIVE);
    /** 把 X 替换为 Y */
    private static final Pattern REPLACE = Pattern.compile(
            "(?:把|将)?\\s*[“\"「']?(.+?)[”\"」']?\\s*(?:替换为|替换成|改为|改成|换成)\\s*[“\"「']?(.+?)[”\"」']?\\s*$");

    /**
     * 解析自然语言指令。
     */
    public DocIntent parse(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return new DocIntent("UNKNOWN", "", List.of(), "指令为空");
        }
        String text = instruction.trim();

        // 识别但不执行的意图，明确告知原因，避免误判为格式指令
        if (containsAny(text, "提取", "摘要", "总结", "归纳", "抽取")) {
            return new DocIntent("EXTRACT", text, List.of(),
                    "信息提取类指令请使用 /api/extract 接口（需配置 LLM Key）");
        }
        if (containsAny(text, "插入表格", "新增表格", "删除表格",
                "插入行", "插入一行", "插入列", "插入一列",
                "删除行", "删除一列", "加一列", "加一行", "删一列", "删一行")) {
            return new DocIntent("TABLE", text, List.of(),
                    "表格结构变更暂未开放（不在指令白名单内）");
        }
        // 破坏性动作明确拒绝，避免被其它关键词误解析后执行
        if (containsAny(text, "删除", "清空", "移除", "销毁", "重置格式")) {
            return new DocIntent("UNKNOWN", text, List.of(),
                    "该操作属于破坏性动作，不在指令白名单内，已拒绝执行");
        }

        String scope = detectScope(text);
        List<DocCommand> commands = new ArrayList<>();

        // 文本替换：含格式关键词时不当作替换，避免「把字号改为小四」被误解析
        boolean hasFormatWord = containsAny(text, "字号", "字体", "加粗", "粗体", "斜体", "下划线", "居中", "对齐");
        if (!hasFormatWord) {
            Matcher m = REPLACE.matcher(text);
            if (m.find()) {
                String from = stripQuotes(m.group(1));
                String to = stripQuotes(m.group(2));
                if (!from.isBlank() && !to.isBlank()) {
                    commands.add(new DocCommand("replace_text", scope, from, to));
                }
            }
        }

        // 加粗（含取消）
        if (text.contains("取消加粗") || text.contains("去掉加粗")) {
            commands.add(DocCommand.of("set_bold", scope, "false"));
        } else if (text.contains("加粗") || text.contains("粗体")) {
            commands.add(DocCommand.of("set_bold", scope, "true"));
        }

        // 斜体
        if (text.contains("斜体")) {
            boolean off = text.contains("取消斜体") || text.contains("去掉斜体");
            commands.add(DocCommand.of("set_italic", scope, String.valueOf(!off)));
        }

        // 下划线
        if (text.contains("下划线")) {
            boolean off = text.contains("取消下划线") || text.contains("去掉下划线");
            commands.add(DocCommand.of("set_underline", scope, String.valueOf(!off)));
        }

        // 字号
        String fontSize = detectFontSize(text);
        if (fontSize != null) {
            commands.add(DocCommand.of("set_font_size", scope, fontSize));
        }

        // 字体
        for (String font : FONT_NAMES) {
            if (text.contains(font)) {
                commands.add(DocCommand.of("set_font_name", scope, font));
                break;
            }
        }

        // 对齐（顺序敏感：先判右/左/两端，再判居中）
        if (text.contains("居中")) {
            commands.add(DocCommand.of("set_align", scope, "center"));
        } else if (text.contains("右对齐")) {
            commands.add(DocCommand.of("set_align", scope, "right"));
        } else if (text.contains("左对齐")) {
            commands.add(DocCommand.of("set_align", scope, "left"));
        } else if (text.contains("两端对齐")) {
            commands.add(DocCommand.of("set_align", scope, "both"));
        }

        if (commands.isEmpty()) {
            return new DocIntent("UNKNOWN", text, List.of(),
                    "未能识别指令，可试试：全文加粗 / 所有段落居中 / 第2段字号改为小四 / 把甲方替换为乙方");
        }

        String intent;
        if (commands.stream().anyMatch(c -> "replace_text".equals(c.action()))) {
            intent = "EDIT";
        } else if (commands.stream().anyMatch(c -> "set_align".equals(c.action()))) {
            intent = "TYPESET";
        } else {
            intent = "FORMAT";
        }
        return new DocIntent(intent, text, commands, "已解析为 " + commands.size() + " 条指令");
    }

    private String detectScope(String text) {
        Matcher p = PARAGRAPH.matcher(text);
        if (p.find()) {
            int index = parseCnNumber(p.group(1));
            if (index > 0) {
                return "paragraph:" + index;
            }
        }
        if (text.contains("最后一段") || text.contains("末段")) {
            return "last_paragraph";
        }
        if (text.contains("第一段") || text.contains("首段")) {
            return "first_paragraph";
        }
        Matcher t = TABLE_INDEX.matcher(text);
        if (t.find()) {
            int index = parseCnNumber(t.group(1));
            if (index > 0) {
                return "table:" + index;
            }
        }
        if (text.contains("标题")) {
            return "headings";
        }
        return "all";
    }

    /**
     * 解析段落/表格序号，支持阿拉伯数字与中文数字（一~九十九）。
     */
    private int parseCnNumber(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        if (s.matches("\\d+")) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        java.util.Map<Character, Integer> digits = Map.of(
                '一', 1, '二', 2, '三', 3, '四', 4, '五', 5, '六', 6, '七', 7, '八', 8, '九', 9);
        if ("十".equals(s)) {
            return 10;
        }
        if (s.charAt(0) == '十') {
            return 10 + digits.getOrDefault(s.length() > 1 ? s.charAt(1) : ' ', 0);
        }
        if (s.length() >= 2 && s.charAt(1) == '十') {
            int tens = digits.getOrDefault(s.charAt(0), 1);
            int ones = s.length() > 2 ? digits.getOrDefault(s.charAt(2), 0) : 0;
            return tens * 10 + ones;
        }
        return digits.getOrDefault(s.charAt(0), -1);
    }

    private String detectFontSize(String text) {
        for (Map.Entry<String, String> e : FONT_SIZE_WORDS.entrySet()) {
            // 「小四」既是字号词，也包含「四号」之外的字形，先精确匹配整词
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        Matcher m = NUMERIC_SIZE.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String stripQuotes(String s) {
        return s == null ? "" : s.trim().replaceAll("^[“\"「']|[”\"」']$", "");
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}

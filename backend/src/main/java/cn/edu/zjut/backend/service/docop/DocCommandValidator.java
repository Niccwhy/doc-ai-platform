package cn.edu.zjut.backend.service.docop;

import cn.edu.zjut.backend.dto.DocCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 指令白名单校验器：执行前拦截未授权动作与非法参数，保证「白名单执行」。
 */
public class DocCommandValidator {

    /** 允许的动作（其余一律拒绝） */
    public static final Set<String> ALLOWED_ACTIONS = Set.of(
            "set_bold", "set_italic", "set_underline",
            "set_font_size", "set_font_name", "set_align", "replace_text");

    private static final Set<String> ALLOWED_ALIGN = Set.of("left", "center", "right", "both");

    private static final Pattern SCOPE = Pattern.compile(
            "^(all|headings|first_paragraph|last_paragraph|paragraph:\\d{1,5}|table:\\d{1,3})$");

    private static final Pattern FONT_NAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z\\s]{0,31}$");

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    /**
     * 校验整批指令。
     *
     * @return 错误信息列表；为空表示全部通过
     */
    public List<String> validate(List<DocCommand> commands) {
        List<String> errors = new ArrayList<>();
        if (commands == null || commands.isEmpty()) {
            errors.add("没有可执行的指令");
            return errors;
        }
        for (int i = 0; i < commands.size(); i++) {
            DocCommand cmd = commands.get(i);
            String prefix = "第 " + (i + 1) + " 条指令：";
            if (cmd == null) {
                errors.add(prefix + "指令为空");
                continue;
            }
            if (cmd.action() == null || !ALLOWED_ACTIONS.contains(cmd.action())) {
                errors.add(prefix + "动作不在白名单内（" + cmd.action() + "）");
                continue;
            }
            if (cmd.scope() == null || !SCOPE.matcher(cmd.scope()).matches()) {
                errors.add(prefix + "作用范围非法（" + cmd.scope() + "）");
                continue;
            }
            errors.addAll(validateParams(cmd, prefix));
        }
        return errors;
    }

    private List<String> validateParams(DocCommand cmd, String prefix) {
        List<String> errors = new ArrayList<>();
        String value = cmd.value();
        switch (cmd.action()) {
            case "set_bold", "set_italic", "set_underline" -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    errors.add(prefix + "开关参数必须为 true 或 false（" + value + "）");
                }
            }
            case "set_font_size" -> {
                try {
                    double size = Double.parseDouble(value);
                    if (size < 6 || size > 72) {
                        errors.add(prefix + "字号需在 6~72 磅之间（" + value + "）");
                    }
                } catch (RuntimeException e) {
                    errors.add(prefix + "字号不是合法数字（" + value + "）");
                }
            }
            case "set_font_name" -> {
                if (value == null || !FONT_NAME.matcher(value).matches()) {
                    errors.add(prefix + "字体名不合法（" + value + "）");
                }
            }
            case "set_align" -> {
                if (value == null || !ALLOWED_ALIGN.contains(value)) {
                    errors.add(prefix + "对齐方式仅支持 left/center/right/both（" + value + "）");
                }
            }
            case "replace_text" -> {
                if (value == null || value.isBlank() || value.length() > 500) {
                    errors.add(prefix + "待替换文本为空或过长");
                } else if (cmd.replaceTo() == null || cmd.replaceTo().isBlank() || cmd.replaceTo().length() > 500) {
                    errors.add(prefix + "替换目标文本为空或过长");
                } else if (CONTROL_CHARS.matcher(value).find() || CONTROL_CHARS.matcher(cmd.replaceTo()).find()) {
                    errors.add(prefix + "文本包含非法控制字符");
                }
            }
            default -> {
                // 白名单已在前置校验，不会到达
            }
        }
        return errors;
    }
}

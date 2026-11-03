package cn.edu.zjut.backend.dto;

/**
 * 文档操作指令（白名单驱动的原子动作）。
 *
 * @param action    动作：set_bold / set_italic / set_underline / set_font_size /
 *                  set_font_name / set_align / replace_text
 * @param scope     作用范围：all / headings / first_paragraph / last_paragraph /
 *                  paragraph:N / table:N（N 从 1 开始）
 * @param value     参数值：true/false、字号、字体名、对齐方式、待替换文本
 * @param replaceTo replace_text 时的替换目标文本
 */
public record DocCommand(
        String action,
        String scope,
        String value,
        String replaceTo
) {

    public static DocCommand of(String action, String scope, String value) {
        return new DocCommand(action, scope, value, null);
    }
}

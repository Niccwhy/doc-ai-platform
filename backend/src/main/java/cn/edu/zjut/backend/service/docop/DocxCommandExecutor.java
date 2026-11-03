package cn.edu.zjut.backend.service.docop;

import cn.edu.zjut.backend.dto.ChangeRecord;
import cn.edu.zjut.backend.dto.DocCommand;
import cn.edu.zjut.backend.dto.ExecutionReport;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * docx 指令执行器（纯 POI）。
 * <p>
 * 在文档副本上执行白名单动作，原文件不动，返回执行后的字节与变更明细，
 * 供「副本试执行 → 预览 → 确认」流程使用。
 */
public class DocxCommandExecutor {

    /**
     * 执行一批指令。
     */
    public ExecutionReport execute(byte[] docx, List<DocCommand> commands) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<ChangeRecord> changes = new ArrayList<>();
            int total = 0;
            for (DocCommand cmd : commands) {
                ChangeRecord record = apply(doc, cmd);
                changes.add(record);
                total += record.affected();
            }
            doc.write(out);
            return new ExecutionReport(out.toByteArray(), changes, total);
        }
    }

    private ChangeRecord apply(XWPFDocument doc, DocCommand cmd) {
        List<XWPFParagraph> paragraphs = selectParagraphs(doc, cmd.scope());
        if (paragraphs.isEmpty()) {
            return new ChangeRecord(cmd.scope(), cmd.action(), "未找到匹配内容", 0);
        }

        return switch (cmd.action()) {
            case "set_bold" -> applyToggle(paragraphs, cmd, "bold");
            case "set_italic" -> applyToggle(paragraphs, cmd, "italic");
            case "set_underline" -> applyToggle(paragraphs, cmd, "underline");
            case "set_font_size" -> applyFontSize(paragraphs, cmd);
            case "set_font_name" -> applyFontName(paragraphs, cmd);
            case "set_align" -> applyAlign(paragraphs, cmd);
            case "replace_text" -> applyReplace(paragraphs, cmd);
            default -> new ChangeRecord(cmd.scope(), cmd.action(), "未授权的动作", 0);
        };
    }

    /**
     * 按作用范围选择段落集合。
     */
    private List<XWPFParagraph> selectParagraphs(XWPFDocument doc, String scope) {
        List<XWPFParagraph> all = doc.getParagraphs();
        if (scope == null) {
            return List.of();
        }
        if (scope.startsWith("paragraph:")) {
            int index = parseIndex(scope);
            if (index < 1 || index > all.size()) {
                return List.of();
            }
            return List.of(all.get(index - 1));
        }
        if (scope.startsWith("table:")) {
            int index = parseIndex(scope);
            if (index < 1 || index > doc.getTables().size()) {
                return List.of();
            }
            return tableParagraphs(doc.getTables().get(index - 1));
        }
        return switch (scope) {
            case "headings" -> all.stream().filter(this::isHeading).toList();
            case "first_paragraph" -> all.isEmpty() ? List.of() : List.of(all.get(0));
            case "last_paragraph" -> all.isEmpty() ? List.of() : List.of(all.get(all.size() - 1));
            default -> all;
        };
    }

    private List<XWPFParagraph> tableParagraphs(XWPFTable table) {
        List<XWPFParagraph> list = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                list.addAll(cell.getParagraphs());
            }
        }
        return list;
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return false;
        }
        String lower = style.toLowerCase();
        return lower.startsWith("heading") || style.startsWith("标题");
    }

    private int parseIndex(String scope) {
        try {
            return Integer.parseInt(scope.substring(scope.indexOf(':') + 1));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private ChangeRecord applyToggle(List<XWPFParagraph> paragraphs, DocCommand cmd, String kind) {
        boolean on = !"false".equals(cmd.value());
        int runs = 0;
        for (XWPFParagraph p : paragraphs) {
            for (XWPFRun run : p.getRuns()) {
                switch (kind) {
                    case "bold" -> run.setBold(on);
                    case "italic" -> run.setItalic(on);
                    default -> run.setUnderline(on ? org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                            : org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
                }
                runs++;
            }
        }
        String actionName = "bold".equals(kind) ? "加粗" : ("italic".equals(kind) ? "斜体" : "下划线");
        return new ChangeRecord(cmd.scope(), cmd.action(),
                actionName + (on ? "已开启" : "已关闭") + "，涉及 " + runs + " 个文本块", runs);
    }

    private ChangeRecord applyFontSize(List<XWPFParagraph> paragraphs, DocCommand cmd) {
        double size = Double.parseDouble(cmd.value());
        int runs = 0;
        for (XWPFParagraph p : paragraphs) {
            for (XWPFRun run : p.getRuns()) {
                run.setFontSize(size);
                runs++;
            }
        }
        return new ChangeRecord(cmd.scope(), cmd.action(),
                "字号设为 " + cmd.value() + " 磅，涉及 " + runs + " 个文本块", runs);
    }

    private ChangeRecord applyFontName(List<XWPFParagraph> paragraphs, DocCommand cmd) {
        String name = cmd.value();
        int runs = 0;
        for (XWPFParagraph p : paragraphs) {
            for (XWPFRun run : p.getRuns()) {
                run.setFontFamily(name);
                run.setFontFamily(name, XWPFRun.FontCharRange.eastAsia);
                runs++;
            }
        }
        return new ChangeRecord(cmd.scope(), cmd.action(),
                "字体设为 " + name + "，涉及 " + runs + " 个文本块", runs);
    }

    private ChangeRecord applyAlign(List<XWPFParagraph> paragraphs, DocCommand cmd) {
        ParagraphAlignment alignment = switch (cmd.value()) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right" -> ParagraphAlignment.RIGHT;
            case "both" -> ParagraphAlignment.BOTH;
            default -> ParagraphAlignment.LEFT;
        };
        for (XWPFParagraph p : paragraphs) {
            p.setAlignment(alignment);
        }
        String label = switch (cmd.value()) {
            case "center" -> "居中";
            case "right" -> "右对齐";
            case "both" -> "两端对齐";
            default -> "左对齐";
        };
        return new ChangeRecord(cmd.scope(), cmd.action(),
                "已设为" + label + "，涉及 " + paragraphs.size() + " 个段落", paragraphs.size());
    }

    /**
     * 文本替换：逐文本块替换，可保留原字体格式；跨文本块拆分的词无法命中，如实报告。
     */
    private ChangeRecord applyReplace(List<XWPFParagraph> paragraphs, DocCommand cmd) {
        String from = cmd.value();
        String to = cmd.replaceTo();
        int replaceCount = 0;
        int hitParagraphs = 0;
        for (XWPFParagraph p : paragraphs) {
            boolean hit = false;
            for (XWPFRun run : p.getRuns()) {
                String text = run.text();
                if (text != null && text.contains(from)) {
                    run.setText(text.replace(from, to), 0);
                    replaceCount += countOccurrences(text, from);
                    hit = true;
                }
            }
            if (hit) {
                hitParagraphs++;
            }
        }
        if (replaceCount == 0) {
            return new ChangeRecord(cmd.scope(), cmd.action(), "未找到待替换文本「" + from + "」", 0);
        }
        return new ChangeRecord(cmd.scope(), cmd.action(),
                "已将「" + from + "」替换为「" + to + "」，共 " + replaceCount + " 处", hitParagraphs);
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) >= 0) {
            count++;
            idx += word.length();
        }
        return count;
    }
}

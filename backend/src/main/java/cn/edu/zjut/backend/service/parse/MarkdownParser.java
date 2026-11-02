package cn.edu.zjut.backend.service.parse;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.dto.DocParseResult.Chunk;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * markdown 解析器：用 flexmark 解析标题与段落，保留标题层级路径。
 */
@Component
public class MarkdownParser implements DocParser {

    private final Parser parser;

    public MarkdownParser() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
    }

    @Override
    public boolean supports(String extension) {
        return "md".equalsIgnoreCase(extension) || "markdown".equalsIgnoreCase(extension);
    }

    @Override
    public DocParseResult parse(byte[] bytes, String fileName) {
        String markdown = new String(bytes, StandardCharsets.UTF_8);
        Document document = parser.parse(markdown);

        List<Chunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        int seq = 0;

        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading) {
                int level = heading.getLevel();
                while (headingStack.size() >= level && !headingStack.isEmpty()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                String title = heading.getText().toString().trim();
                headingStack.add(title);
                chunks.add(new Chunk(seq++, "text", joinPath(headingStack), title, null, null, null));
            } else {
                String text = node.getChars().toString().trim();
                if (!text.isEmpty()) {
                    chunks.add(new Chunk(seq++, "text", joinPath(headingStack), text, null, null, null));
                }
            }
        }
        return new DocParseResult(fileName, "md", chunks.size(), chunks);
    }

    private String joinPath(List<String> stack) {
        return String.join(" > ", stack);
    }
}

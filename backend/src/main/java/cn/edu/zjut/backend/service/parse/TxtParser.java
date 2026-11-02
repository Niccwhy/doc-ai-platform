package cn.edu.zjut.backend.service.parse;

import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.dto.DocParseResult.Chunk;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * txt 解析器：按行/空行分段，无结构，整篇作为少量 text chunk。
 */
@Component
public class TxtParser implements DocParser {

    @Override
    public boolean supports(String extension) {
        return "txt".equalsIgnoreCase(extension);
    }

    @Override
    public DocParseResult parse(byte[] bytes, String fileName) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<Chunk> chunks = new ArrayList<>();
        int seq = 0;

        // 按空行分段
        String[] paragraphs = text.split("\\n\\s*\\n");
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(new Chunk(seq++, "text", "", trimmed, null, null, null));
            }
        }
        // 空文件兜底
        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.add(new Chunk(seq++, "text", "", text.trim(), null, null, null));
        }
        return new DocParseResult(fileName, "txt", chunks.size(), chunks);
    }
}

package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.DocParseResult;
import cn.edu.zjut.backend.service.parse.DocParser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档解析服务：根据文件扩展名分发到对应解析器。
 */
@Service
public class ParseService {

    private final List<DocParser> parsers;

    public ParseService(List<DocParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 根据文件名解析文档。
     */
    public DocParseResult parse(byte[] bytes, String fileName) {
        String extension = extensionOf(fileName);
        DocParser parser = parsers.stream()
                .filter(p -> p.supports(extension))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型: " + extension));

        try {
            return parser.parse(bytes, fileName);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "解析失败: " + e.getMessage());
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}

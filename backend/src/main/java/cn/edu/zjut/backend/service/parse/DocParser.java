package cn.edu.zjut.backend.service.parse;

import cn.edu.zjut.backend.dto.DocParseResult;

/**
 * 文档解析器接口。每种格式实现一个解析器。
 */
public interface DocParser {

    /**
     * 是否支持该文件扩展名（不含点，小写）。
     */
    boolean supports(String extension);

    /**
     * 解析文件，返回统一结构。
     */
    DocParseResult parse(byte[] bytes, String fileName) throws Exception;
}

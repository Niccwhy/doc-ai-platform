package cn.edu.zjut.backend.dto;

import java.util.List;

/**
 * 指令执行结果：回填后的文档字节 + 逐条变更明细。
 *
 * @param content   执行后的文档字节（预览副本，原文件未改动）
 * @param changes   逐条变更明细
 * @param affected  总影响数量
 */
public record ExecutionReport(
        byte[] content,
        List<ChangeRecord> changes,
        int affected
) {
}

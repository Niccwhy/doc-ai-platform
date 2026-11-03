package cn.edu.zjut.backend.dto;

import java.util.List;

/**
 * 文档操作预览结果：在副本上试执行后的变更摘要。
 * 确认后才落盘为新版本（预览优先、可回滚）。
 *
 * @param token       预览令牌，用于确认执行
 * @param docId       文档标识（由原文件名生成，用于累积版本）
 * @param intent      解析出的意图类型
 * @param message     解析说明
 * @param changes     逐条变更明细
 * @param affected    总影响数量
 * @param hasChanges  是否产生了实际变更
 */
public record DocPreview(
        String token,
        String docId,
        String intent,
        String message,
        List<ChangeRecord> changes,
        int affected,
        boolean hasChanges
) {
}

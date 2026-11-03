package cn.edu.zjut.backend.dto;

import java.util.List;

/**
 * 自然语言指令的解析结果。
 *
 * @param intent      意图类型：FORMAT / EDIT / TYPESET / EXTRACT / TABLE / UNKNOWN
 * @param instruction 原始指令
 * @param commands    解析出的原子指令序列（已通过白名单校验才可执行）
 * @param message     解析说明或不可执行原因
 */
public record DocIntent(
        String intent,
        String instruction,
        List<DocCommand> commands,
        String message
) {
}

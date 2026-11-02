package cn.edu.zjut.backend.dto;

import java.time.LocalDateTime;

/**
 * 演示用 DTO，展示 Java 21 record 的写法（不可变、自动生成构造器/访问器）。
 */
public record DemoDto(Long id, String name, LocalDateTime createdAt) {
}

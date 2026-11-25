package cn.edu.zjut.backend.email.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ImportAttachmentsRequest(@NotEmpty List<@NotNull Long> attachmentIds) {
}
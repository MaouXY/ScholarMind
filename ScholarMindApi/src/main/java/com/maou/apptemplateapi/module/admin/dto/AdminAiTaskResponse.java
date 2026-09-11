package com.maou.apptemplateapi.module.admin.dto;

import java.time.LocalDateTime;

public record AdminAiTaskResponse(
        Long id,
        String taskType,
        String bizType,
        Long bizId,
        String modelName,
        String status,
        Integer durationMs,
        Integer requestChars,
        Integer responseChars,
        String requestPreview,
        String responsePreview,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.maou.apptemplateapi.module.admin.dto;

public record AdminAiTaskHealthResponse(
        String taskType,
        Long total,
        Long success,
        Long failed,
        Long timeout,
        Double successRate,
        Long avgDurationMs
) {
}

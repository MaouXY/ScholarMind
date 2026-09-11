package com.maou.apptemplateapi.module.ai.service;

public record StructuredAiResult<T>(
        T value,
        String rawJson,
        Long taskId,
        int attemptCount,
        boolean repaired,
        boolean fallbackUsed
) {
}

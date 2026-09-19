package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

/**
 * 我的书房——阅读历史条目（带文献基本信息，供列表直接展示）。
 */
public record ReadingHistoryItemResponse(
        Long id,
        Long literatureId,
        String literatureTitle,
        String author,
        String categoryName,
        String status,
        String visibility,
        Integer lastPage,
        Integer progress,
        Integer readDuration,
        LocalDateTime updatedAt
) {
}

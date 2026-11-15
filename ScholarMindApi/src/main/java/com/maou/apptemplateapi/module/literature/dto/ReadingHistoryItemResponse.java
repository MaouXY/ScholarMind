package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

/**
 * 我的书房——阅读历史条目（带文献基本信息，供列表直接展示）。
 * <p>文献上传即发布后不再有可见性与审核状态，条目上的 status / visibility 已移除。</p>
 */
public record ReadingHistoryItemResponse(
        Long id,
        Long literatureId,
        String literatureTitle,
        String author,
        String categoryName,
        Integer lastPage,
        Integer progress,
        Integer readDuration,
        LocalDateTime updatedAt
) {
}

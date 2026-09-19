package com.maou.apptemplateapi.module.citation.dto;

import java.time.LocalDateTime;

/**
 * 已收藏的引用记录。
 */
public record CitationRecordResponse(
        Long id,
        Long literatureId,
        String literatureTitle,
        String style,
        String styleLabel,
        String citationText,
        String note,
        LocalDateTime createdAt
) {
}

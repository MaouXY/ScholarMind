package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record LiteratureResponse(
        Long id,
        String title,
        String author,
        String source,
        Integer publishYear,
        String language,
        Long categoryId,
        String categoryName,
        Long uploaderId,
        String visibility,
        String status,
        Long fileId,
        String fileUrl,
        String fileType,
        Long fileSize,
        String abstractText,
        String aiSummary,
        String keywords,
        Integer readCount,
        Integer downloadCount,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

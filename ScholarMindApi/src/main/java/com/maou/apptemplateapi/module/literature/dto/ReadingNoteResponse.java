package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record ReadingNoteResponse(
        Long id,
        Long literatureId,
        String literatureTitle,
        String title,
        String content,
        Integer aiGenerated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

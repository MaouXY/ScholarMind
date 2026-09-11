package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record ReadingHistoryResponse(
        Long id,
        Long literatureId,
        Integer lastPage,
        Integer progress,
        Integer readDuration,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

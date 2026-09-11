package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record LiteratureSummaryResponse(
        Long literatureId,
        String summary,
        String status,
        LocalDateTime summaryUpdatedAt
) {
}

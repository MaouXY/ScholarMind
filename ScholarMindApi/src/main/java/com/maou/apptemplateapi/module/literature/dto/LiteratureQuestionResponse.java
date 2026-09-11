package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LiteratureQuestionResponse(
        Long recordId,
        Long literatureId,
        String question,
        String answer,
        List<LiteratureReferenceChunkResponse> references,
        LocalDateTime createdAt
) {
}

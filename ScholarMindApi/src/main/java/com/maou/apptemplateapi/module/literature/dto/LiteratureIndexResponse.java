package com.maou.apptemplateapi.module.literature.dto;

public record LiteratureIndexResponse(
        Long literatureId,
        int chunkCount,
        String parseStatus
) {
}

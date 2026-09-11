package com.maou.apptemplateapi.module.literature.dto;

public record LiteratureReferenceChunkResponse(
        Long id,
        Long literatureId,
        Integer chunkIndex,
        String content,
        double score
) {
}

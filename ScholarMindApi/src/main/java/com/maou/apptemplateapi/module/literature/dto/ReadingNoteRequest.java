package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReadingNoteRequest(
        @NotNull Long literatureId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        Integer aiGenerated
) {
}

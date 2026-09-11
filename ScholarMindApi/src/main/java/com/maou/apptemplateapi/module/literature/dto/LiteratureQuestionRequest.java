package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiteratureQuestionRequest(
        @NotBlank @Size(max = 1000) String question,
        Integer limit
) {
}

package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiteratureSearchRequest(
        @NotBlank @Size(max = 1000) String query,
        Long categoryId,
        Integer limit
) {
}

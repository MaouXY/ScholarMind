package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LiteratureCategoryRequest(
        @NotBlank @Size(max = 64) String name,
        Long parentId,
        Integer sort,
        @NotNull Integer status
) {
}

package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LiteratureRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String author,
        @Size(max = 255) String source,
        Integer publishYear,
        @Size(max = 32) String language,
        Long categoryId,
        @Pattern(regexp = "PRIVATE|PUBLIC", message = "visibility must be PRIVATE or PUBLIC") String visibility,
        Long fileId,
        String abstractText,
        String keywords
) {
}

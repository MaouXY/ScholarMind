package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.Size;

public record LiteratureReviewRequest(
        @Size(max = 500) String reason
) {
}

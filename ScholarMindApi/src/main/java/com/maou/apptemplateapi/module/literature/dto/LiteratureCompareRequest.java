package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LiteratureCompareRequest(
        @NotEmpty @Size(min = 2, max = 6) List<Long> literatureIds
) {
}

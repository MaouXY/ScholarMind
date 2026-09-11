package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReadingHistoryRequest(
        @Min(0) Integer lastPage,
        @Min(0) @Max(100) Integer progress,
        @Min(0) Integer readDuration
) {
}

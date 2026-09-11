package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

public record StructuredSearchIntent(
        List<String> keywords,
        String language,
        Integer startYear,
        Integer endYear,
        String sort
) {
}

package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

public record StructuredLiteratureSummary(
        String background,
        String problem,
        String method,
        String conclusion,
        String innovation,
        List<String> keywords,
        String suitableReaders
) {
}

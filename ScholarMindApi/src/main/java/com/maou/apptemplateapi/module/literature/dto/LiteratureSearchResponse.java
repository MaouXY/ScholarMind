package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

public record LiteratureSearchResponse(
        String query,
        List<LiteratureResponse> literatures,
        List<LiteratureReferenceChunkResponse> references,
        String parsedIntent
) {
}

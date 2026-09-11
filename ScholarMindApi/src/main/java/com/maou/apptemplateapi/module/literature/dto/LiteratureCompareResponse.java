package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

public record LiteratureCompareResponse(
        List<Long> literatureIds,
        String analysis
) {
}

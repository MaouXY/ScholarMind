package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class LiteratureSearchController {

    private final LiteratureAiService literatureAiService;

    @PostMapping("/semantic")
    public ApiResponse<LiteratureSearchResponse> semantic(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.semanticSearch(request));
    }

    @PostMapping("/natural-language")
    public ApiResponse<LiteratureSearchResponse> naturalLanguage(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.naturalLanguageSearch(request));
    }
}

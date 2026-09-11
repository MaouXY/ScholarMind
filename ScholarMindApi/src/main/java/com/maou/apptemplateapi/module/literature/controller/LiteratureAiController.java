package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.dto.*;
import com.maou.apptemplateapi.module.literature.service.LiteratureAiService;
import com.maou.apptemplateapi.module.literature.service.LiteratureIndexService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/literature")
public class LiteratureAiController {

    private final LiteratureAiService literatureAiService;
    private final LiteratureIndexService literatureIndexService;

    @PostMapping("/{literatureId}/index")
    public ApiResponse<LiteratureIndexResponse> rebuildIndex(@PathVariable Long literatureId) {
        return ApiResponse.success(literatureIndexService.rebuild(literatureId));
    }

    @PostMapping("/{literatureId}/summary")
    public ApiResponse<LiteratureSummaryResponse> summary(@PathVariable Long literatureId) {
        return ApiResponse.success(literatureAiService.generateSummary(literatureId));
    }

    @PostMapping("/{literatureId}/question")
    public ApiResponse<LiteratureQuestionResponse> question(
            @PathVariable Long literatureId,
            @Valid @RequestBody LiteratureQuestionRequest request) {
        return ApiResponse.success(literatureAiService.answer(literatureId, request));
    }

    @GetMapping("/qa-records")
    public ApiResponse<List<LiteratureQuestionResponse>> qaHistory(
            @RequestParam(required = false) Long literatureId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(literatureAiService.qaHistory(literatureId, page, size));
    }

    @PostMapping("/compare")
    public ApiResponse<LiteratureCompareResponse> compare(@Valid @RequestBody LiteratureCompareRequest request) {
        return ApiResponse.success(literatureAiService.compare(request));
    }
}

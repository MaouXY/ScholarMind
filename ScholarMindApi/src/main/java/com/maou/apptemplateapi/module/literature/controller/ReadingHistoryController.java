package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryResponse;
import com.maou.apptemplateapi.module.literature.service.ReadingHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reading-history")
public class ReadingHistoryController {

    private final ReadingHistoryService readingHistoryService;

    @PutMapping("/{literatureId}")
    public ApiResponse<ReadingHistoryResponse> upsert(
            @PathVariable Long literatureId,
            @Valid @RequestBody ReadingHistoryRequest request) {
        return ApiResponse.success(readingHistoryService.upsert(literatureId, request));
    }

    @GetMapping("/{literatureId}")
    public ApiResponse<ReadingHistoryResponse> detail(@PathVariable Long literatureId) {
        return ApiResponse.success(readingHistoryService.getByLiterature(literatureId));
    }
}

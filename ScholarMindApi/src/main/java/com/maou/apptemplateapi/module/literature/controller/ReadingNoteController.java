package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteResponse;
import com.maou.apptemplateapi.module.literature.service.ReadingNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notes")
public class ReadingNoteController {

    private final ReadingNoteService readingNoteService;

    @PostMapping
    public ApiResponse<ReadingNoteResponse> create(@Valid @RequestBody ReadingNoteRequest request) {
        return ApiResponse.success(readingNoteService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReadingNoteResponse> update(@PathVariable Long id, @Valid @RequestBody ReadingNoteRequest request) {
        return ApiResponse.success(readingNoteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        readingNoteService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<ReadingNoteResponse>> list(
            @RequestParam(required = false) Long literatureId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(readingNoteService.list(literatureId, page, size));
    }
}

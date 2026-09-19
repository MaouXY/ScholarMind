package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.citation.dto.CitationStyleResponse;
import com.maou.apptemplateapi.module.citation.service.CitationService;
import com.maou.apptemplateapi.module.literature.dto.LiteratureChunkResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureDetailResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureDownloadResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureDetailService;
import com.maou.apptemplateapi.module.literature.service.LiteratureDownloadService;
import com.maou.apptemplateapi.module.literature.service.LiteratureIndexService;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/literatures")
public class LiteratureController {

    private final LiteratureService literatureService;
    private final LiteratureDetailService literatureDetailService;
    private final LiteratureIndexService literatureIndexService;
    private final LiteratureDownloadService literatureDownloadService;
    private final CitationService citationService;

    @GetMapping
    public ApiResponse<PageResult<LiteratureResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility) {
        return ApiResponse.success(literatureService.list(page, size, keyword, categoryId, status, visibility));
    }

    @GetMapping("/{id}")
    public ApiResponse<LiteratureResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(literatureService.detail(id));
    }

    @GetMapping("/{id}/view")
    public ApiResponse<LiteratureDetailResponse> detailView(@PathVariable Long id) {
        return ApiResponse.success(literatureDetailService.detailView(id));
    }

    @GetMapping("/{id}/chunks")
    public ApiResponse<List<LiteratureChunkResponse>> chunks(@PathVariable Long id) {
        return ApiResponse.success(literatureIndexService.listChunks(id));
    }

    @GetMapping("/{id}/citations")
    public ApiResponse<List<CitationStyleResponse>> citations(@PathVariable Long id) {
        return ApiResponse.success(citationService.stylesForLiterature(id));
    }

    @GetMapping("/{id}/download")
    public ApiResponse<LiteratureDownloadResponse> download(@PathVariable Long id) {
        return ApiResponse.success(literatureDownloadService.download(id));
    }

    @PostMapping
    public ApiResponse<LiteratureResponse> create(@Valid @RequestBody LiteratureRequest request) {
        return ApiResponse.success(literatureService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LiteratureResponse> update(@PathVariable Long id, @Valid @RequestBody LiteratureRequest request) {
        return ApiResponse.success(literatureService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        literatureService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<LiteratureResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(literatureService.submit(id));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<LiteratureResponse> read(@PathVariable Long id) {
        return ApiResponse.success(literatureService.increaseReadCount(id));
    }
}

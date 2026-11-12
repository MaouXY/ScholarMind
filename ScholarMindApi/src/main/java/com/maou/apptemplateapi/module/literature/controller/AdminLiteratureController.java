package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureReviewRequest;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/literatures")
public class AdminLiteratureController {

    private final LiteratureService literatureService;

    @GetMapping("/pending")
    public ApiResponse<PageResult<LiteratureResponse>> pending(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(literatureService.pending(page, size));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<LiteratureResponse> approve(@PathVariable Long id) {
        return ApiResponse.success(literatureService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<LiteratureResponse> reject(@PathVariable Long id, @Valid @RequestBody LiteratureReviewRequest request) {
        return ApiResponse.success(literatureService.reject(id, request));
    }

    /** 管理端删除文献：级联清理分块、收藏、阅读历史、笔记、引用、问答、附件与向量。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        literatureService.adminDelete(id);
        return ApiResponse.success();
    }
}

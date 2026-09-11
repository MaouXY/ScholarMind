package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.literature.dto.FavoriteResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/favorites")
public class LiteratureFavoriteController {

    private final LiteratureFavoriteService literatureFavoriteService;

    @PostMapping("/{literatureId}")
    public ApiResponse<FavoriteResponse> favorite(@PathVariable Long literatureId) {
        return ApiResponse.success(literatureFavoriteService.favorite(literatureId));
    }

    @DeleteMapping("/{literatureId}")
    public ApiResponse<Void> unfavorite(@PathVariable Long literatureId) {
        literatureFavoriteService.unfavorite(literatureId);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<FavoriteResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(literatureFavoriteService.list(page, size));
    }
}

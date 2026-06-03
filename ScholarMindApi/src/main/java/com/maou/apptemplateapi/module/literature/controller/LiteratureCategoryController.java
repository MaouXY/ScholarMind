package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureCategoryRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureCategoryResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/literature-categories")
public class LiteratureCategoryController {

    private final LiteratureCategoryService categoryService;

    @GetMapping
    public ApiResponse<List<LiteratureCategoryResponse>> list(@RequestParam(defaultValue = "true") boolean onlyEnabled) {
        return ApiResponse.success(categoryService.list(onlyEnabled));
    }

    @PostMapping
    public ApiResponse<LiteratureCategoryResponse> create(@Valid @RequestBody LiteratureCategoryRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LiteratureCategoryResponse> update(@PathVariable Long id, @Valid @RequestBody LiteratureCategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success();
    }
}

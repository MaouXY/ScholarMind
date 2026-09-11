package com.maou.apptemplateapi.module.admin.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskSummaryResponse;
import com.maou.apptemplateapi.module.admin.service.AdminAiTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai-tasks")
public class AdminAiTaskController {

    private final AdminAiTaskService adminAiTaskService;

    @GetMapping
    public ApiResponse<PageResult<AdminAiTaskResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminAiTaskService.list(page, size, taskType, status, keyword));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminAiTaskSummaryResponse> summary(@RequestParam(required = false) Integer days) {
        return ApiResponse.success(adminAiTaskService.summary(days));
    }
}

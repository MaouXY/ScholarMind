package com.maou.apptemplateapi.module.admin.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminDashboardResponse;
import com.maou.apptemplateapi.module.admin.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminStatisticsService.dashboard(days, limit));
    }
}

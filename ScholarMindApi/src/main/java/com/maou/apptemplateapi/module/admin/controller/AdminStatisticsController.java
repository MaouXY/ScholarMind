package com.maou.apptemplateapi.module.admin.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminDashboardResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminKeywordStatResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminOperationReportResponse;
import com.maou.apptemplateapi.module.admin.service.AdminOperationReportService;
import com.maou.apptemplateapi.module.admin.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;
    private final AdminOperationReportService adminOperationReportService;

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminStatisticsService.dashboard(days, limit));
    }

    @GetMapping("/keywords")
    public ApiResponse<List<AdminKeywordStatResponse>> keywords(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminStatisticsService.keywords(days, limit));
    }

    @PostMapping("/report")
    public ApiResponse<AdminOperationReportResponse> report(
            @RequestBody(required = false) AdminReportRequest request) {
        return ApiResponse.success(adminOperationReportService.generate(request == null ? null : request.days()));
    }

    public record AdminReportRequest(Integer days) {
    }
}

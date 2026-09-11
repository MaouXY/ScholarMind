package com.maou.apptemplateapi.module.admin.dto;

import java.util.List;

public record AdminDashboardResponse(
        AdminOverviewResponse overview,
        List<AdminTrendPointResponse> trends,
        List<AdminDimensionStatResponse> literatureStatusStats,
        List<AdminDimensionStatResponse> literatureCategoryStats,
        List<AdminDimensionStatResponse> userRoleStats,
        List<AdminHotLiteratureResponse> hotLiteratures,
        List<AdminActiveUserResponse> activeUsers,
        List<AdminAiTaskHealthResponse> aiTaskHealth
) {
}

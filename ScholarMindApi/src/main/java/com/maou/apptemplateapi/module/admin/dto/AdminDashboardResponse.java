package com.maou.apptemplateapi.module.admin.dto;

import java.util.List;

/**
 * 管理端仪表盘聚合结果。
 * <p>文献上传即发布后不再有审核状态维度，{@code literatureStatusStats}
 * 改为按「文献类别」（期刊论文 / 会议论文 / 学位论文 / 图书 / 预印本 / 其他）统计。</p>
 */
public record AdminDashboardResponse(
        AdminOverviewResponse overview,
        List<AdminTrendPointResponse> trends,
        List<AdminDimensionStatResponse> literatureTypeStats,
        List<AdminDimensionStatResponse> literatureCategoryStats,
        List<AdminDimensionStatResponse> userRoleStats,
        List<AdminHotLiteratureResponse> hotLiteratures,
        List<AdminActiveUserResponse> activeUsers,
        List<AdminAiTaskHealthResponse> aiTaskHealth
) {
}

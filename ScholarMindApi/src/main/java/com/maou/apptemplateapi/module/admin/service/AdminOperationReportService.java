package com.maou.apptemplateapi.module.admin.service;

import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.admin.dto.AdminActiveUserResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskHealthResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminDashboardResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminHotLiteratureResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminKeywordStatResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminOperationReportResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminOverviewResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminTrendPointResponse;
import com.maou.apptemplateapi.module.admin.dto.StructuredOperationReport;
import com.maou.apptemplateapi.module.ai.service.StructuredAiResult;
import com.maou.apptemplateapi.module.ai.service.StructuredAiService;
import com.maou.apptemplateapi.module.ai.service.StructuredAiService.StructuredAiSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 第五阶段：AI 运营分析报告。
 * 以管理端统计指标为唯一事实来源，交给大模型生成结构化分析；
 * 模型不可用或输出不合法时，退化为基于同一份指标的本地兜底报告，保证管理端始终可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationReportService {

    private static final String TASK_OPERATION_REPORT = "ADMIN_OPERATION_REPORT";
    private static final String BIZ_TYPE_ADMIN_STATISTICS = "ADMIN_STATISTICS";
    private static final String SCHEMA_NAME = "admin_operation_report_v1";
    private static final int DEFAULT_DAYS = 14;
    private static final int MAX_DAYS = 30;
    private static final int TOP_LIMIT = 8;

    private final AdminStatisticsService adminStatisticsService;
    private final StructuredAiService structuredAiService;

    public AdminOperationReportResponse generate(Integer days) {
        CurrentUser admin = requireAdmin("admin-operation-report");
        int normalizedDays = Math.min(Math.max(days == null ? DEFAULT_DAYS : days, 1), MAX_DAYS);
        AdminDashboardResponse dashboard = adminStatisticsService.dashboard(normalizedDays, TOP_LIMIT);
        List<AdminKeywordStatResponse> keywords = adminStatisticsService.keywords(normalizedDays, TOP_LIMIT);
        String metrics = metricsText(dashboard, keywords, normalizedDays);

        StructuredAiResult<StructuredOperationReport> result = structuredAiService.generate(new StructuredAiSpec<>(
                admin.getOrganizationId(),
                TASK_OPERATION_REPORT,
                BIZ_TYPE_ADMIN_STATISTICS,
                admin.getId(),
                SCHEMA_NAME,
                systemPrompt(),
                userPrompt(metrics),
                schemaPrompt(),
                StructuredOperationReport.class,
                this::validate,
                () -> fallbackReport(dashboard, keywords, normalizedDays)
        ));
        StructuredOperationReport report = result.value();
        log.info("admin operation report generated, scenario=admin-operation-report, adminId={}, days={}, taskId={}, attemptCount={}, repaired={}, fallbackUsed={}, sectionCount={}",
                admin.getId(), normalizedDays, result.taskId(), result.attemptCount(), result.repaired(), result.fallbackUsed(),
                report.sections() == null ? 0 : report.sections().size());
        return new AdminOperationReportResponse(
                LocalDateTime.now(),
                normalizedDays,
                result.fallbackUsed(),
                report.summary(),
                safeList(report.highlights()),
                safeList(report.risks()),
                safeList(report.suggestions()),
                toSections(report)
        );
    }

    private String metricsText(AdminDashboardResponse dashboard,
                               List<AdminKeywordStatResponse> keywords,
                               int days) {
        AdminOverviewResponse overview = dashboard.overview();
        StringBuilder builder = new StringBuilder();
        builder.append("统计窗口: 最近 ").append(days).append(" 天\n");
        builder.append("用户: 总数=").append(overview.userTotal())
                .append(", 学生=").append(overview.studentTotal())
                .append(", 教师=").append(overview.teacherTotal())
                .append(", 启用账号=").append(overview.activeUserTotal()).append('\n');
        builder.append("文献: 总数=").append(overview.literatureTotal())
                .append(", 已发布=").append(overview.publishedLiteratureTotal())
                .append(", 待审核=").append(overview.pendingReviewTotal())
                .append(", 私有=").append(overview.privateLiteratureTotal()).append('\n');
        builder.append("阅读沉淀: 收藏=").append(overview.favoriteTotal())
                .append(", 阅读历史=").append(overview.readingHistoryTotal())
                .append(", 笔记=").append(overview.readingNoteTotal())
                .append(", 问答=").append(overview.qaTotal())
                .append(", 知识库文档=").append(overview.ragDocumentTotal()).append('\n');
        builder.append("文件资产: 数量=").append(overview.fileTotal())
                .append(", 总大小=").append(fileSizeBytesAsLong(overview)).append(" 字节\n");
        builder.append("AI 任务: 总数=").append(overview.aiTaskTotal())
                .append(", 成功=").append(overview.aiTaskSuccessTotal())
                .append(", 失败=").append(overview.aiTaskFailedTotal())
                .append(", 成功率=").append(overview.aiSuccessRate()).append("%")
                .append(", 平均耗时=").append(overview.avgAiDurationMs()).append("ms\n");
        builder.append("窗口内新增: 用户=").append(sumTrend(dashboard.trends(), AdminTrendPointResponse::newUsers))
                .append(", 文献=").append(sumTrend(dashboard.trends(), AdminTrendPointResponse::newLiteratures))
                .append(", 阅读事件=").append(sumTrend(dashboard.trends(), AdminTrendPointResponse::readingEvents))
                .append(", 问答=").append(sumTrend(dashboard.trends(), AdminTrendPointResponse::qaEvents))
                .append(", AI 任务=").append(sumTrend(dashboard.trends(), AdminTrendPointResponse::aiTasks)).append('\n');

        if (dashboard.hotLiteratures() != null && !dashboard.hotLiteratures().isEmpty()) {
            builder.append("热门文献:\n");
            for (AdminHotLiteratureResponse item : dashboard.hotLiteratures()) {
                builder.append("- ").append(item.title())
                        .append("（阅读=").append(item.readCount())
                        .append(", 下载=").append(item.downloadCount())
                        .append(", 收藏=").append(item.favoriteCount())
                        .append(", 笔记=").append(item.noteCount())
                        .append(", 状态=").append(item.status()).append("）\n");
            }
        }
        if (dashboard.activeUsers() != null && !dashboard.activeUsers().isEmpty()) {
            builder.append("活跃用户:\n");
            for (AdminActiveUserResponse item : dashboard.activeUsers()) {
                builder.append("- ").append(StringUtils.hasText(item.nickname()) ? item.nickname() : item.username())
                        .append("（角色=").append(item.role())
                        .append(", 活跃分=").append(item.activityScore())
                        .append(", 阅读=").append(item.readingHistoryCount())
                        .append(", 笔记=").append(item.noteCount())
                        .append(", 问答=").append(item.qaCount()).append("）\n");
            }
        }
        if (dashboard.aiTaskHealth() != null && !dashboard.aiTaskHealth().isEmpty()) {
            builder.append("AI 任务类型健康度:\n");
            for (AdminAiTaskHealthResponse item : dashboard.aiTaskHealth()) {
                builder.append("- ").append(item.taskType())
                        .append("（总数=").append(item.total())
                        .append(", 成功=").append(item.success())
                        .append(", 失败=").append(item.failed())
                        .append(", 超时=").append(item.timeout())
                        .append(", 成功率=").append(item.successRate()).append("%")
                        .append(", 平均耗时=").append(item.avgDurationMs()).append("ms）\n");
            }
        }
        if (keywords != null && !keywords.isEmpty()) {
            builder.append("热门关键词（文献标签 / 检索 / 问答命中）:\n");
            for (AdminKeywordStatResponse item : keywords) {
                builder.append("- ").append(item.keyword())
                        .append("（文献=").append(item.literatureCount())
                        .append(", 检索=").append(item.searchCount())
                        .append(", 问答=").append(item.qaCount())
                        .append(", 合计=").append(item.total()).append("）\n");
            }
        }
        return builder.toString();
    }

    private StructuredOperationReport fallbackReport(AdminDashboardResponse dashboard,
                                                     List<AdminKeywordStatResponse> keywords,
                                                     int days) {
        AdminOverviewResponse overview = dashboard.overview();
        long newUsers = sumTrend(dashboard.trends(), AdminTrendPointResponse::newUsers);
        long newLiteratures = sumTrend(dashboard.trends(), AdminTrendPointResponse::newLiteratures);
        long readingEvents = sumTrend(dashboard.trends(), AdminTrendPointResponse::readingEvents);
        long qaEvents = sumTrend(dashboard.trends(), AdminTrendPointResponse::qaEvents);
        double aiSuccessRate = overview.aiSuccessRate() == null ? 0 : overview.aiSuccessRate();
        long avgAiDuration = overview.avgAiDurationMs() == null ? 0 : overview.avgAiDurationMs();
        long pendingReview = overview.pendingReviewTotal() == null ? 0 : overview.pendingReviewTotal();
        long literatureTotal = overview.literatureTotal() == null ? 0 : overview.literatureTotal();
        long favoriteTotal = overview.favoriteTotal() == null ? 0 : overview.favoriteTotal();
        long readingNoteTotal = overview.readingNoteTotal() == null ? 0 : overview.readingNoteTotal();
        long qaTotal = overview.qaTotal() == null ? 0 : overview.qaTotal();

        List<String> highlights = new ArrayList<>();
        highlights.add("最近 %d 天新增用户 %d 人、新增文献 %d 篇，阅读事件 %d 次、问答 %d 次。"
                .formatted(days, newUsers, newLiteratures, readingEvents, qaEvents));
        highlights.add("平台累计文献 %d 篇，其中已发布 %d 篇，待审核 %d 篇。"
                .formatted(literatureTotal, overview.publishedLiteratureTotal(), pendingReview));
        highlights.add("阅读沉淀累计：收藏 %d、阅读笔记 %d、问答记录 %d。"
                .formatted(favoriteTotal, readingNoteTotal, qaTotal));
        highlights.add("AI 任务成功率 %.2f%%，平均耗时 %d ms。".formatted(aiSuccessRate, avgAiDuration));
        if (keywords != null && !keywords.isEmpty()) {
            highlights.add("热门关键词：" + joinTopKeywords(keywords));
        }

        List<String> risks = new ArrayList<>();
        if (pendingReview > 0) {
            risks.add("有 %d 篇公开文献积压在待审核队列，建议当天处理，避免影响内容上线节奏。".formatted(pendingReview));
        }
        if (overview.aiTaskTotal() != null && overview.aiTaskTotal() > 0 && aiSuccessRate < 95) {
            risks.add("AI 任务成功率 %.2f%% 低于 95%% 目标，建议检查模型配额、提示词与结构化输出校验链路。".formatted(aiSuccessRate));
        }
        if (avgAiDuration > 5000) {
            risks.add("AI 平均耗时 %d ms 偏高，问答与摘要体验可能受影响，建议评估超时与降级策略。".formatted(avgAiDuration));
        }
        if (literatureTotal > 0 && favoriteTotal == 0 && readingNoteTotal == 0) {
            risks.add("文献已有一定规模但缺少收藏与笔记沉淀，说明阅读闭环使用率不足。");
        }
        if (qaTotal == 0) {
            risks.add("尚无问答记录，需确认文献分块索引与向量检索是否已成功构建。");
        }
        if (risks.isEmpty()) {
            risks.add("当前指标未发现明显风险，建议持续观察审核积压、AI 成功率与阅读活跃度。");
        }

        List<String> suggestions = new ArrayList<>();
        if (pendingReview > 0) {
            suggestions.add("优先清理待审核队列，可结合管理端统计看板按提交时间排序批量处理。");
        }
        if (keywords != null && !keywords.isEmpty()) {
            suggestions.add("围绕热门关键词补充知识库文档与专题文献包，提升检索命中率。");
        }
        if (readingEvents > 0 && readingNoteTotal < readingEvents) {
            suggestions.add("在阅读页强化笔记与引用导出的引导，将阅读行为转化为可沉淀的笔记资产。");
        }
        suggestions.add("定期回看 AI 任务类型健康度，对失败率偏高的任务类型单独优化提示词与兜底策略。");
        suggestions.add("结合活跃用户榜单做内容推荐，将高活跃用户的阅读主题作为采购与加工线索。");

        List<StructuredOperationReport.Section> sections = new ArrayList<>();
        sections.add(new StructuredOperationReport.Section("一、平台整体规模",
                "累计用户 %d 人（学生 %d、教师 %d），累计文献 %d 篇，已发布 %d 篇，文件资产 %d 个，总大小 %d 字节。"
                        .formatted(overview.userTotal(), overview.studentTotal(), overview.teacherTotal(),
                                literatureTotal, overview.publishedLiteratureTotal(), overview.fileTotal(), fileSizeBytesAsLong(overview))));
        sections.add(new StructuredOperationReport.Section("二、窗口内增长与阅读活跃度",
                "最近 %d 天新增用户 %d 人、新增文献 %d 篇，产生阅读事件 %d 次、问答 %d 次、AI 任务 %d 次。"
                        .formatted(days, newUsers, newLiteratures, readingEvents, qaEvents,
                                sumTrend(dashboard.trends(), AdminTrendPointResponse::aiTasks))));
        sections.add(new StructuredOperationReport.Section("三、AI 能力运行情况",
                "AI 任务累计 %d 次，成功 %d 次、失败 %d 次，成功率 %.2f%%，平均耗时 %d ms；覆盖任务类型 %d 类。"
                        .formatted(overview.aiTaskTotal(), overview.aiTaskSuccessTotal(), overview.aiTaskFailedTotal(),
                                aiSuccessRate, avgAiDuration,
                                dashboard.aiTaskHealth() == null ? 0 : dashboard.aiTaskHealth().size())));
        sections.add(new StructuredOperationReport.Section("四、内容偏好与检索关键词",
                keywords == null || keywords.isEmpty()
                        ? "统计窗口内暂无足够的关键词数据，建议积累检索与问答行为后再观察内容偏好。"
                        : "热门关键词：" + joinTopKeywords(keywords) + "。可据此规划知识库补充与文献推荐策略。"));
        sections.add(new StructuredOperationReport.Section("五、风险与后续行动",
                String.join("；", risks) + " " + String.join("；", suggestions)));

        String summary = "最近 %d 天平台运行平稳：新增用户 %d 人、新增文献 %d 篇，AI 任务成功率 %.2f%%，待审核文献 %d 篇。"
                .formatted(days, newUsers, newLiteratures, aiSuccessRate, pendingReview);
        return new StructuredOperationReport(summary, highlights, risks, suggestions, sections);
    }

    private String joinTopKeywords(List<AdminKeywordStatResponse> keywords) {
        return keywords.stream()
                .limit(5)
                .map(item -> "%s(%d)".formatted(item.keyword(), item.total()))
                .reduce((left, right) -> left + "、" + right)
                .orElse("暂无");
    }

    /**
     * 文件总字节在 DTO 中用 Double 承载（全局 Long→String 保护 ID 精度，数值指标必须用数字类型），
     * 报告文本仍需按“整数字节”拼接，因此在这里取整，保持指标文本与既有格式一致。
     */
    private long fileSizeBytesAsLong(AdminOverviewResponse overview) {
        return overview.fileSizeBytes() == null ? 0L : overview.fileSizeBytes().longValue();
    }

    private long sumTrend(List<AdminTrendPointResponse> trends, ToLongFunction<AdminTrendPointResponse> getter) {
        if (trends == null || trends.isEmpty()) {
            return 0;
        }
        return trends.stream().mapToLong(getter).sum();
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).toList();
    }

    private List<AdminOperationReportResponse.Section> toSections(StructuredOperationReport report) {
        if (report.sections() == null) {
            return List.of();
        }
        return report.sections().stream()
                .map(section -> new AdminOperationReportResponse.Section(section.title(), section.content()))
                .toList();
    }

    private void validate(StructuredOperationReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is null");
        }
        requireText(report.summary(), "summary");
        requireList(report.highlights(), "highlights");
        requireList(report.risks(), "risks");
        requireList(report.suggestions(), "suggestions");
        if (report.sections() == null || report.sections().isEmpty()) {
            throw new IllegalArgumentException("sections is empty");
        }
        for (StructuredOperationReport.Section section : report.sections()) {
            if (section == null) {
                throw new IllegalArgumentException("section is null");
            }
            requireText(section.title(), "section.title");
            requireText(section.content(), "section.content");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is blank");
        }
    }

    private void requireList(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
    }

    private String systemPrompt() {
        return "You are an academic library operations analyst. Return only one JSON object that matches the required schema. Use Chinese. Base every statement strictly on the supplied metrics; never invent data, trends, or user behaviour that is not present in the metrics.";
    }

    private String userPrompt(String metrics) {
        return """
                Required schema and validation rules:
                %s

                Platform metrics:
                %s
                """.formatted(schemaPrompt(), metrics);
    }

    private String schemaPrompt() {
        return """
                {
                  "summary": "string, required, 40-200 Chinese characters, overall operating conclusion of the window",
                  "highlights": ["3-5 short Chinese strings, notable positive facts taken from the metrics"],
                  "risks": ["1-5 short Chinese strings, risks or problems visible in the metrics"],
                  "suggestions": ["3-6 short Chinese strings, concrete actionable next steps"],
                  "sections": [
                    { "title": "string, required, Chinese section title", "content": "string, required, 40-260 Chinese characters" }
                  ]
                }
                Validation: sections must contain 3-6 items; every list item must be non-blank; do not repeat the same sentence in different fields.
                """;
    }

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("user role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }
}

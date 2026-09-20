package com.maou.apptemplateapi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.admin.dto.*;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import com.maou.apptemplateapi.module.ai.mapper.AiTaskRecordMapper;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.literature.entity.*;
import com.maou.apptemplateapi.module.literature.mapper.*;
import com.maou.apptemplateapi.module.rag.entity.RagKnowledgeDocument;
import com.maou.apptemplateapi.module.rag.mapper.RagKnowledgeDocumentMapper;
import com.maou.apptemplateapi.module.user.entity.UserAccount;
import com.maou.apptemplateapi.module.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int DEFAULT_KEYWORD_DAYS = 30;
    private static final int DEFAULT_KEYWORD_LIMIT = 20;
    private static final int MAX_KEYWORD_LIMIT = 50;

    private final UserAccountMapper userAccountMapper;
    private final LiteratureMapper literatureMapper;
    private final LiteratureCategoryMapper categoryMapper;
    private final LiteratureFavoriteMapper favoriteMapper;
    private final ReadingHistoryMapper readingHistoryMapper;
    private final ReadingNoteMapper readingNoteMapper;
    private final LiteratureQaRecordMapper qaRecordMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final AiTaskRecordMapper aiTaskRecordMapper;
    private final RagKnowledgeDocumentMapper ragKnowledgeDocumentMapper;
    private final LiteratureSearchLogMapper searchLogMapper;

    public AdminDashboardResponse dashboard(Integer days, Integer limit) {
        CurrentUser admin = requireAdmin("admin-statistics-dashboard");
        int normalizedDays = normalize(days, DEFAULT_DAYS, MAX_DAYS);
        int normalizedLimit = normalize(limit, DEFAULT_LIMIT, MAX_LIMIT);
        AdminDashboardResponse response = new AdminDashboardResponse(
                overview(),
                trends(normalizedDays),
                literatureStatusStats(),
                literatureCategoryStats(normalizedLimit),
                userRoleStats(),
                hotLiteratures(normalizedLimit),
                activeUsers(normalizedLimit),
                aiTaskHealth()
        );
        log.info("admin statistics dashboard queried, scenario=admin-statistics-dashboard, adminId={}, days={}, limit={}",
                admin.getId(), normalizedDays, normalizedLimit);
        return response;
    }

    /**
     * 热门关键词统计：汇总“文献标签热度 + 检索行为热度 + 问答提问命中热度”。
     * 关键词词表来自文献 keywords 字段与检索日志的解析关键词，问答热度按该词表在窗口内提问中的命中次数计算。
     */
    public List<AdminKeywordStatResponse> keywords(Integer days, Integer limit) {
        CurrentUser admin = requireAdmin("admin-statistics-keywords");
        int normalizedDays = normalize(days, DEFAULT_KEYWORD_DAYS, MAX_DAYS);
        int normalizedLimit = normalize(limit, DEFAULT_KEYWORD_LIMIT, MAX_KEYWORD_LIMIT);
        LocalDate start = LocalDate.now().minusDays(normalizedDays - 1L);

        Map<String, KeywordCounter> counters = new LinkedHashMap<>();
        List<Literature> literatures = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .eq(Literature::getDeleted, 0));
        for (Literature literature : literatures) {
            for (String keyword : splitKeywords(literature.getKeywords())) {
                counters.computeIfAbsent(keyword, KeywordCounter::new).literatureCount++;
            }
        }

        List<LiteratureSearchLog> searchLogs = searchLogMapper.selectList(new LambdaQueryWrapper<LiteratureSearchLog>()
                .eq(LiteratureSearchLog::getDeleted, 0)
                .ge(LiteratureSearchLog::getCreatedAt, start.atStartOfDay()));
        for (LiteratureSearchLog searchLog : searchLogs) {
            for (String keyword : splitKeywords(searchLog.getKeywords())) {
                counters.computeIfAbsent(keyword, KeywordCounter::new).searchCount++;
            }
        }

        List<LiteratureQaRecord> qaRecords = qaRecordMapper.selectList(new LambdaQueryWrapper<LiteratureQaRecord>()
                .eq(LiteratureQaRecord::getDeleted, 0)
                .ge(LiteratureQaRecord::getCreatedAt, start.atStartOfDay()));
        if (!qaRecords.isEmpty() && !counters.isEmpty()) {
            List<String> questions = qaRecords.stream()
                    .map(LiteratureQaRecord::getQuestion)
                    .filter(StringUtils::hasText)
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .toList();
            for (Map.Entry<String, KeywordCounter> entry : counters.entrySet()) {
                for (String question : questions) {
                    if (question.contains(entry.getKey())) {
                        entry.getValue().qaCount++;
                    }
                }
            }
        }

        List<AdminKeywordStatResponse> result = counters.values().stream()
                .map(KeywordCounter::toResponse)
                .sorted(Comparator.comparingLong((AdminKeywordStatResponse item) -> item.total())
                        .reversed()
                        .thenComparing(AdminKeywordStatResponse::keyword))
                .limit(normalizedLimit)
                .toList();
        log.info("admin keyword statistics queried, scenario=admin-statistics-keywords, adminId={}, days={}, limit={}, keywordTotal={}, literatureTotal={}, searchLogTotal={}, qaRecordTotal={}",
                admin.getId(), normalizedDays, normalizedLimit, counters.size(), literatures.size(), searchLogs.size(), qaRecords.size());
        return result;
    }

    private List<String> splitKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split("[\\s,，;；、/|]+"))
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> item.length() >= 2 && item.length() <= 32)
                .distinct()
                .limit(10)
                .toList();
    }

    private static final class KeywordCounter {

        private final String keyword;
        private long literatureCount;
        private long searchCount;
        private long qaCount;

        private KeywordCounter(String keyword) {
            this.keyword = keyword;
        }

        private AdminKeywordStatResponse toResponse() {
            return new AdminKeywordStatResponse(keyword, (int) literatureCount, (int) searchCount, (int) qaCount,
                    (int) (literatureCount + searchCount + qaCount));
        }
    }

    private AdminOverviewResponse overview() {
        long aiTaskTotal = countAiTasks(null);
        long aiSuccess = countAiTasks("SUCCESS");
        long aiFailed = countAiTasks("FAILED");
        long avgDurationMs = aiTaskTotal == 0 ? 0L : avgAiDuration(null);
        return new AdminOverviewResponse(
                toInt(countUsers(null, null)),
                toInt(countUsers("STUDENT", null)),
                toInt(countUsers("TEACHER", null)),
                toInt(countUsers(null, 1)),
                toInt(countLiteratures(null, null)),
                toInt(countLiteratures("PUBLISHED", null)),
                toInt(countLiteratures("PENDING_REVIEW", null)),
                toInt(countLiteratures(null, "PRIVATE")),
                toInt(countFiles()),
                // 文件总字节可能超过 int 范围，用 Double 承载（可精确表示到 2^53）
                (double) sumFileSize(),
                toInt(countFavorites()),
                toInt(countReadingHistories()),
                toInt(countReadingNotes()),
                toInt(countQaRecords()),
                toInt(countRagDocuments()),
                toInt(aiTaskTotal),
                toInt(aiSuccess),
                toInt(aiFailed),
                rate(aiSuccess, aiTaskTotal),
                toInt(avgDurationMs)
        );
    }

    private List<AdminTrendPointResponse> trends(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<UserAccount> users = userAccountMapper.selectList(createdAfter(UserAccount::getCreatedAt, UserAccount::getDeleted, start));
        List<Literature> literatures = literatureMapper.selectList(createdAfter(Literature::getCreatedAt, Literature::getDeleted, start));
        List<ReadingHistory> histories = readingHistoryMapper.selectList(updatedAfter(ReadingHistory::getUpdatedAt, ReadingHistory::getDeleted, start));
        List<LiteratureQaRecord> qaRecords = qaRecordMapper.selectList(createdAfter(LiteratureQaRecord::getCreatedAt, LiteratureQaRecord::getDeleted, start));
        List<AiTaskRecord> aiTasks = aiTaskRecordMapper.selectList(createdAfter(AiTaskRecord::getCreatedAt, AiTaskRecord::getDeleted, start));

        Map<LocalDate, Long> userCounts = groupByDate(users, UserAccount::getCreatedAt);
        Map<LocalDate, Long> literatureCounts = groupByDate(literatures, Literature::getCreatedAt);
        Map<LocalDate, Long> historyCounts = groupByDate(histories, ReadingHistory::getUpdatedAt);
        Map<LocalDate, Long> qaCounts = groupByDate(qaRecords, LiteratureQaRecord::getCreatedAt);
        Map<LocalDate, Long> aiCounts = groupByDate(aiTasks, AiTaskRecord::getCreatedAt);

        List<AdminTrendPointResponse> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            result.add(new AdminTrendPointResponse(
                    date.toString(),
                    toInt(userCounts.getOrDefault(date, 0L)),
                    toInt(literatureCounts.getOrDefault(date, 0L)),
                    toInt(historyCounts.getOrDefault(date, 0L)),
                    toInt(qaCounts.getOrDefault(date, 0L)),
                    toInt(aiCounts.getOrDefault(date, 0L))
            ));
        }
        return result;
    }

    private List<AdminDimensionStatResponse> literatureStatusStats() {
        return literatureMapper.selectList(new LambdaQueryWrapper<Literature>().eq(Literature::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(item -> value(item.getStatus(), "UNKNOWN"), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new AdminDimensionStatResponse(entry.getKey(), toInt(entry.getValue())))
                .toList();
    }

    private List<AdminDimensionStatResponse> literatureCategoryStats(int limit) {
        Map<Long, LiteratureCategory> categoryMap = categoryMapper.selectList(new LambdaQueryWrapper<LiteratureCategory>()
                        .eq(LiteratureCategory::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(LiteratureCategory::getId, Function.identity()));
        return literatureMapper.selectList(new LambdaQueryWrapper<Literature>().eq(Literature::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(item -> categoryName(item.getCategoryId(), categoryMap), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new AdminDimensionStatResponse(entry.getKey(), toInt(entry.getValue())))
                .toList();
    }

    private List<AdminDimensionStatResponse> userRoleStats() {
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(item -> value(item.getRole(), "UNKNOWN"), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new AdminDimensionStatResponse(entry.getKey(), toInt(entry.getValue())))
                .toList();
    }

    private List<AdminHotLiteratureResponse> hotLiteratures(int limit) {
        List<Literature> literatures = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .eq(Literature::getDeleted, 0)
                .orderByDesc(Literature::getReadCount)
                .orderByDesc(Literature::getDownloadCount)
                .orderByDesc(Literature::getUpdatedAt)
                .last("limit " + limit));
        Map<Long, LiteratureCategory> categoryMap = categoryMapper.selectList(new LambdaQueryWrapper<LiteratureCategory>()
                        .eq(LiteratureCategory::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(LiteratureCategory::getId, Function.identity()));
        Map<Long, Long> favoriteCounts = countFavoritesByLiterature(literatures);
        Map<Long, Long> noteCounts = countNotesByLiterature(literatures);
        return literatures.stream()
                .map(item -> new AdminHotLiteratureResponse(
                        item.getId(),
                        item.getTitle(),
                        item.getAuthor(),
                        categoryName(item.getCategoryId(), categoryMap),
                        item.getStatus(),
                        item.getVisibility(),
                        item.getReadCount() == null ? 0 : item.getReadCount(),
                        item.getDownloadCount() == null ? 0 : item.getDownloadCount(),
                        toInt(favoriteCounts.getOrDefault(item.getId(), 0L)),
                        toInt(noteCounts.getOrDefault(item.getId(), 0L))
                ))
                .toList();
    }

    private List<AdminActiveUserResponse> activeUsers(int limit) {
        List<UserAccount> users = userAccountMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getDeleted, 0)
                .eq(UserAccount::getStatus, 1));
        Map<Long, Long> uploadCounts = countLiteraturesByUploader();
        Map<Long, Long> historyCounts = countReadingHistoriesByUser();
        Map<Long, Long> favoriteCounts = countFavoritesByUser();
        Map<Long, Long> noteCounts = countNotesByUser();
        Map<Long, Long> qaCounts = countQaByUser();
        return users.stream()
                .map(user -> {
                    long uploads = uploadCounts.getOrDefault(user.getId(), 0L);
                    long histories = historyCounts.getOrDefault(user.getId(), 0L);
                    long favorites = favoriteCounts.getOrDefault(user.getId(), 0L);
                    long notes = noteCounts.getOrDefault(user.getId(), 0L);
                    long qa = qaCounts.getOrDefault(user.getId(), 0L);
                    long score = uploads * 5 + histories * 2 + favorites * 2 + notes * 3 + qa * 4;
                    return new AdminActiveUserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                            toInt(uploads), toInt(histories), toInt(favorites), toInt(notes), toInt(qa), toInt(score));
                })
                .sorted(Comparator.comparingInt(AdminActiveUserResponse::activityScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<AdminAiTaskHealthResponse> aiTaskHealth() {
        List<AiTaskRecord> tasks = aiTaskRecordMapper.selectList(new LambdaQueryWrapper<AiTaskRecord>()
                .eq(AiTaskRecord::getDeleted, 0));
        return tasks.stream()
                .collect(Collectors.groupingBy(item -> value(item.getTaskType(), "UNKNOWN")))
                .entrySet()
                .stream()
                .map(entry -> toAiTaskHealth(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(AdminAiTaskHealthResponse::total).reversed())
                .toList();
    }

    private AdminAiTaskHealthResponse toAiTaskHealth(String taskType, List<AiTaskRecord> tasks) {
        long total = tasks.size();
        long success = tasks.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        long failed = tasks.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        long timeout = tasks.stream().filter(item -> "TIMEOUT".equals(item.getStatus())).count();
        long avgDuration = Math.round(tasks.stream()
                .map(AiTaskRecord::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
        return new AdminAiTaskHealthResponse(taskType, toInt(total), toInt(success), toInt(failed), toInt(timeout),
                rate(success, total), toInt(avgDuration));
    }

    private long countUsers(String role, Integer status) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getDeleted, 0)
                .eq(StringUtils.hasText(role), UserAccount::getRole, role)
                .eq(status != null, UserAccount::getStatus, status));
    }

    private long countLiteratures(String status, String visibility) {
        return literatureMapper.selectCount(new LambdaQueryWrapper<Literature>()
                .eq(Literature::getDeleted, 0)
                .eq(StringUtils.hasText(status), Literature::getStatus, status)
                .eq(StringUtils.hasText(visibility), Literature::getVisibility, visibility));
    }

    private long countFiles() {
        return fileMetadataMapper.selectCount(new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getDeleted, 0));
    }

    private long sumFileSize() {
        return fileMetadataMapper.selectList(new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getDeleted, 0))
                .stream()
                .map(FileMetadata::getSizeBytes)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private long countFavorites() {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<LiteratureFavorite>().eq(LiteratureFavorite::getDeleted, 0));
    }

    private long countReadingHistories() {
        return readingHistoryMapper.selectCount(new LambdaQueryWrapper<ReadingHistory>().eq(ReadingHistory::getDeleted, 0));
    }

    private long countReadingNotes() {
        return readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>().eq(ReadingNote::getDeleted, 0));
    }

    private long countQaRecords() {
        return qaRecordMapper.selectCount(new LambdaQueryWrapper<LiteratureQaRecord>().eq(LiteratureQaRecord::getDeleted, 0));
    }

    private long countRagDocuments() {
        return ragKnowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<RagKnowledgeDocument>().eq(RagKnowledgeDocument::getDeleted, 0));
    }

    private long countAiTasks(String status) {
        return aiTaskRecordMapper.selectCount(new LambdaQueryWrapper<AiTaskRecord>()
                .eq(AiTaskRecord::getDeleted, 0)
                .eq(StringUtils.hasText(status), AiTaskRecord::getStatus, status));
    }

    private Long avgAiDuration(String taskType) {
        return Math.round(aiTaskRecordMapper.selectList(new LambdaQueryWrapper<AiTaskRecord>()
                        .eq(AiTaskRecord::getDeleted, 0)
                        .eq(StringUtils.hasText(taskType), AiTaskRecord::getTaskType, taskType))
                .stream()
                .map(AiTaskRecord::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
    }

    private <T> LambdaQueryWrapper<T> createdAfter(com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, LocalDateTime> column,
                                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Integer> deletedColumn,
                                                   LocalDate start) {
        return new LambdaQueryWrapper<T>()
                .ge(column, start.atStartOfDay())
                .eq(deletedColumn, 0);
    }

    private <T> LambdaQueryWrapper<T> updatedAfter(com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, LocalDateTime> column,
                                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Integer> deletedColumn,
                                                   LocalDate start) {
        return new LambdaQueryWrapper<T>()
                .ge(column, start.atStartOfDay())
                .eq(deletedColumn, 0);
    }

    private <T> Map<LocalDate, Long> groupByDate(List<T> values, Function<T, LocalDateTime> timeGetter) {
        return values.stream()
                .map(timeGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
    }

    private Map<Long, Long> countFavoritesByLiterature(List<Literature> literatures) {
        Set<Long> literatureIds = ids(literatures, Literature::getId);
        if (literatureIds.isEmpty()) {
            return Map.of();
        }
        return favoriteMapper.selectList(new LambdaQueryWrapper<LiteratureFavorite>()
                        .eq(LiteratureFavorite::getDeleted, 0)
                        .in(LiteratureFavorite::getLiteratureId, literatureIds))
                .stream()
                .collect(Collectors.groupingBy(LiteratureFavorite::getLiteratureId, Collectors.counting()));
    }

    private Map<Long, Long> countNotesByLiterature(List<Literature> literatures) {
        Set<Long> literatureIds = ids(literatures, Literature::getId);
        if (literatureIds.isEmpty()) {
            return Map.of();
        }
        return readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                        .eq(ReadingNote::getDeleted, 0)
                        .in(ReadingNote::getLiteratureId, literatureIds))
                .stream()
                .collect(Collectors.groupingBy(ReadingNote::getLiteratureId, Collectors.counting()));
    }

    private Map<Long, Long> countLiteraturesByUploader() {
        return literatureMapper.selectList(new LambdaQueryWrapper<Literature>().eq(Literature::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(Literature::getUploaderId, Collectors.counting()));
    }

    private Map<Long, Long> countReadingHistoriesByUser() {
        return readingHistoryMapper.selectList(new LambdaQueryWrapper<ReadingHistory>().eq(ReadingHistory::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(ReadingHistory::getUserId, Collectors.counting()));
    }

    private Map<Long, Long> countFavoritesByUser() {
        return favoriteMapper.selectList(new LambdaQueryWrapper<LiteratureFavorite>().eq(LiteratureFavorite::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(LiteratureFavorite::getUserId, Collectors.counting()));
    }

    private Map<Long, Long> countNotesByUser() {
        return readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>().eq(ReadingNote::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(ReadingNote::getUserId, Collectors.counting()));
    }

    private Map<Long, Long> countQaByUser() {
        return qaRecordMapper.selectList(new LambdaQueryWrapper<LiteratureQaRecord>().eq(LiteratureQaRecord::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(LiteratureQaRecord::getUserId, Collectors.counting()));
    }

    private Set<Long> ids(List<Literature> literatures, Function<Literature, Long> getter) {
        return literatures.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String categoryName(Long categoryId, Map<Long, LiteratureCategory> categoryMap) {
        if (categoryId == null) {
            return "未分类";
        }
        LiteratureCategory category = categoryMap.get(categoryId);
        return category == null ? "未分类" : category.getName();
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }

    /**
     * 全局 Jackson 会把 Long 序列化为字符串（保护雪花 ID 精度），
     * 因此统计口径的计数与耗时在装配响应时统一收敛为 Integer，保证前端拿到数字。
     * 计数规模远小于 int 上限，转换是安全的。
     */
    private static int toInt(long value) {
        return (int) value;
    }

    private int normalize(Integer value, int defaultValue, int maxValue) {
        return Math.min(Math.max(value == null ? defaultValue : value, 1), maxValue);
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

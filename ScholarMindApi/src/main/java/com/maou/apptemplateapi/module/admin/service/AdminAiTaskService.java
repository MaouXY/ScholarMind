package com.maou.apptemplateapi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskHealthResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskResponse;
import com.maou.apptemplateapi.module.admin.dto.AdminAiTaskSummaryResponse;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import com.maou.apptemplateapi.module.ai.mapper.AiTaskRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 调用日志查询：为管理端提供 AI 任务的分页检索与全局健康度概要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiTaskService {

    private static final int PREVIEW_CHARS = 400;
    private static final long DEFAULT_PAGE_SIZE = 10;
    private static final long MAX_PAGE_SIZE = 100;
    private static final int MAX_DAYS = 90;

    private final AiTaskRecordMapper aiTaskRecordMapper;

    public PageResult<AdminAiTaskResponse> list(long page,
                                                long size,
                                                String taskType,
                                                String status,
                                                String keyword) {
        CurrentUser admin = requireAdmin("admin-ai-task-list");
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiTaskRecord> wrapper = new LambdaQueryWrapper<AiTaskRecord>()
                .eq(AiTaskRecord::getDeleted, 0)
                .eq(StringUtils.hasText(taskType), AiTaskRecord::getTaskType, taskType)
                .eq(StringUtils.hasText(status), AiTaskRecord::getStatus, status)
                .and(StringUtils.hasText(keyword), nested -> nested
                        .like(AiTaskRecord::getTaskType, keyword)
                        .or().like(AiTaskRecord::getBizType, keyword)
                        .or().like(AiTaskRecord::getRequestSnapshot, keyword)
                        .or().like(AiTaskRecord::getResponseSnapshot, keyword)
                        .or().like(AiTaskRecord::getErrorMessage, keyword))
                .orderByDesc(AiTaskRecord::getCreatedAt)
                .orderByDesc(AiTaskRecord::getId);
        Page<AiTaskRecord> result = aiTaskRecordMapper.selectPage(new Page<>(normalizedPage, normalizedSize), wrapper);
        log.info("admin ai task list queried, scenario=admin-ai-task-list, adminId={}, page={}, size={}, taskType={}, status={}, keywordProvided={}, total={}",
                admin.getId(), normalizedPage, normalizedSize, taskType, status, StringUtils.hasText(keyword), result.getTotal());
        List<AdminAiTaskResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(items, normalizedPage, normalizedSize, result.getTotal());
    }

    public AdminAiTaskSummaryResponse summary(Integer days) {
        CurrentUser admin = requireAdmin("admin-ai-task-summary");
        // 注意：days 为 null 时不能调用 startTime，否则 Integer 拆箱会抛 NPE
        LocalDateTime start = days == null ? null : startTime(days);
        LambdaQueryWrapper<AiTaskRecord> wrapper = new LambdaQueryWrapper<AiTaskRecord>()
                .eq(AiTaskRecord::getDeleted, 0)
                .ge(start != null, AiTaskRecord::getCreatedAt, start);
        List<AiTaskRecord> tasks = aiTaskRecordMapper.selectList(wrapper);
        long total = tasks.size();
        long success = countStatus(tasks, "SUCCESS");
        long failed = countStatus(tasks, "FAILED");
        long timeout = countStatus(tasks, "TIMEOUT");
        long running = tasks.stream()
                .filter(item -> "PENDING".equals(item.getStatus()) || "RUNNING".equals(item.getStatus()))
                .count();
        long avgDurationMs = Math.round(tasks.stream()
                .map(AiTaskRecord::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
        List<AdminAiTaskHealthResponse> items = tasks.stream()
                .collect(Collectors.groupingBy(item -> StringUtils.hasText(item.getTaskType()) ? item.getTaskType() : "UNKNOWN"))
                .entrySet()
                .stream()
                .map(entry -> toHealth(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(AdminAiTaskHealthResponse::total).reversed())
                .toList();
        log.info("admin ai task summary queried, scenario=admin-ai-task-summary, adminId={}, days={}, total={}, success={}, failed={}, timeout={}, running={}",
                admin.getId(), days, total, success, failed, timeout, running);
        return new AdminAiTaskSummaryResponse(toInt(total), toInt(success), toInt(failed), toInt(timeout), toInt(running),
                rate(success, total), toInt(avgDurationMs), items);
    }

    private AdminAiTaskHealthResponse toHealth(String taskType, List<AiTaskRecord> tasks) {
        long total = tasks.size();
        long success = countStatus(tasks, "SUCCESS");
        long failed = countStatus(tasks, "FAILED");
        long timeout = countStatus(tasks, "TIMEOUT");
        long avgDuration = Math.round(tasks.stream()
                .map(AiTaskRecord::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
        return new AdminAiTaskHealthResponse(taskType, toInt(total), toInt(success), toInt(failed), toInt(timeout),
                rate(success, total), toInt(avgDuration));
    }

    private AdminAiTaskResponse toResponse(AiTaskRecord record) {
        return new AdminAiTaskResponse(
                record.getId(),
                record.getTaskType(),
                record.getBizType(),
                record.getBizId(),
                record.getModelName(),
                record.getStatus(),
                toInt(record.getDurationMs()),
                length(record.getRequestSnapshot()),
                length(record.getResponseSnapshot()),
                preview(record.getRequestSnapshot()),
                preview(record.getResponseSnapshot()),
                record.getErrorMessage(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private long countStatus(List<AiTaskRecord> tasks, String status) {
        return tasks.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private Integer length(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 全局 Jackson 配置会把 Long 序列化为字符串（保护雪花 ID 精度），
     * 因此这里把统计类数值统一转为 Integer，保证前端拿到的就是数字。
     */
    private Integer toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String preview(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_CHARS ? normalized : normalized.substring(0, PREVIEW_CHARS) + "…";
    }

    private LocalDateTime startTime(Integer days) {
        int normalizedDays = Math.min(Math.max(days, 1), MAX_DAYS);
        return LocalDateTime.now().minusDays(normalizedDays - 1L).toLocalDate().atStartOfDay();
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
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

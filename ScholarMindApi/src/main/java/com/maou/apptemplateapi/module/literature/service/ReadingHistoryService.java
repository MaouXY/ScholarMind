package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryItemResponse;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.entity.ReadingHistory;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.mapper.ReadingHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingHistoryService {

    private final ReadingHistoryMapper readingHistoryMapper;
    private final LiteratureMapper literatureMapper;
    private final LiteratureCategoryMapper categoryMapper;
    private final LiteratureService literatureService;

    @Transactional
    public ReadingHistoryResponse upsert(Long literatureId, ReadingHistoryRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        literatureService.requireReadableLiterature(literatureId, currentUser, "reading-history-upsert");
        validateRequest(literatureId, currentUser.getId(), request);
        ReadingHistory history = readingHistoryMapper.selectOne(new LambdaQueryWrapper<ReadingHistory>()
                .eq(ReadingHistory::getUserId, currentUser.getId())
                .eq(ReadingHistory::getLiteratureId, literatureId)
                .eq(ReadingHistory::getDeleted, 0)
                .last("limit 1"));
        if (history == null) {
            history = new ReadingHistory();
            history.setUserId(currentUser.getId());
            history.setLiteratureId(literatureId);
            history.setLastPage(valueOrDefault(request.lastPage(), 0));
            history.setProgress(valueOrDefault(request.progress(), 0));
            history.setReadDuration(valueOrDefault(request.readDuration(), 0));
            readingHistoryMapper.insert(history);
            log.info("reading history created, scenario=reading-history-upsert, literatureId={}, userId={}, historyId={}, lastPage={}, progress={}, readDuration={}",
                    literatureId, currentUser.getId(), history.getId(), history.getLastPage(), history.getProgress(), history.getReadDuration());
        } else {
            history.setLastPage(request.lastPage() != null ? request.lastPage() : history.getLastPage());
            history.setProgress(request.progress() != null ? request.progress() : history.getProgress());
            history.setReadDuration(request.readDuration() != null ? request.readDuration() : history.getReadDuration());
            readingHistoryMapper.updateById(history);
            log.info("reading history updated, scenario=reading-history-upsert, literatureId={}, userId={}, historyId={}, lastPage={}, progress={}, readDuration={}",
                    literatureId, currentUser.getId(), history.getId(), history.getLastPage(), history.getProgress(), history.getReadDuration());
        }
        return toResponse(readingHistoryMapper.selectById(history.getId()));
    }

    public ReadingHistoryResponse getByLiterature(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        literatureService.requireReadableLiterature(literatureId, currentUser, "reading-history-detail");
        ReadingHistory history = readingHistoryMapper.selectOne(new LambdaQueryWrapper<ReadingHistory>()
                .eq(ReadingHistory::getUserId, currentUser.getId())
                .eq(ReadingHistory::getLiteratureId, literatureId)
                .eq(ReadingHistory::getDeleted, 0)
                .last("limit 1"));
        return history == null ? null : toResponse(history);
    }

    /**
     * 我的书房——当前用户的阅读历史列表（按最近阅读时间倒序，带文献基本信息）。
     */
    public PageResult<ReadingHistoryItemResponse> list(long page, long size) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Page<ReadingHistory> result = readingHistoryMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<ReadingHistory>()
                        .eq(ReadingHistory::getUserId, currentUser.getId())
                        .eq(ReadingHistory::getDeleted, 0)
                        .orderByDesc(ReadingHistory::getUpdatedAt)
                        .orderByDesc(ReadingHistory::getId));
        Map<Long, Literature> literatureMap = loadLiteratures(result.getRecords().stream()
                .map(ReadingHistory::getLiteratureId)
                .distinct()
                .toList());
        Map<Long, String> categoryMap = loadCategoryNames(literatureMap.values().stream()
                .map(Literature::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<ReadingHistoryItemResponse> items = result.getRecords().stream()
                .map(history -> {
                    Literature literature = literatureMap.get(history.getLiteratureId());
                    boolean readable = literature != null && literature.getDeleted() == 0;
                    return new ReadingHistoryItemResponse(
                            history.getId(),
                            history.getLiteratureId(),
                            readable && literature.getTitle() != null ? literature.getTitle() : "文献已删除或不可见",
                            readable ? literature.getAuthor() : null,
                            readable && literature.getCategoryId() != null ? categoryMap.get(literature.getCategoryId()) : null,
                            readable ? literature.getStatus() : null,
                            readable ? literature.getVisibility() : null,
                            history.getLastPage(),
                            history.getProgress(),
                            history.getReadDuration(),
                            history.getUpdatedAt());
                })
                .toList();
        log.info("reading history listed, scenario=reading-history-list, userId={}, page={}, size={}, total={}",
                currentUser.getId(), result.getCurrent(), result.getSize(), result.getTotal());
        return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    private Map<Long, Literature> loadLiteratures(List<Long> literatureIds) {
        if (literatureIds.isEmpty()) {
            return Map.of();
        }
        return literatureMapper.selectBatchIds(literatureIds).stream()
                .collect(Collectors.toMap(Literature::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, String> loadCategoryNames(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<LiteratureCategory>()
                        .in(LiteratureCategory::getId, categoryIds)
                        .eq(LiteratureCategory::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(LiteratureCategory::getId, LiteratureCategory::getName, (left, right) -> left));
    }

    private void validateRequest(Long literatureId, Long userId, ReadingHistoryRequest request) {
        Integer lastPage = request.lastPage();
        Integer progress = request.progress();
        Integer readDuration = request.readDuration();
        if ((lastPage != null && lastPage < 0)
                || (progress != null && (progress < 0 || progress > 100))
                || (readDuration != null && readDuration < 0)) {
            log.warn("reading history invalid, scenario=reading-history-upsert, literatureId={}, userId={}, lastPage={}, progress={}, readDuration={}",
                    literatureId, userId, lastPage, progress, readDuration);
            throw new BusinessException(ErrorCode.READING_PROGRESS_INVALID);
        }
    }

    private Integer valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private ReadingHistoryResponse toResponse(ReadingHistory history) {
        return new ReadingHistoryResponse(history.getId(), history.getLiteratureId(), history.getLastPage(),
                history.getProgress(), history.getReadDuration(), history.getCreatedAt(), history.getUpdatedAt());
    }
}

package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryResponse;
import com.maou.apptemplateapi.module.literature.entity.ReadingHistory;
import com.maou.apptemplateapi.module.literature.mapper.ReadingHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingHistoryService {

    private final ReadingHistoryMapper readingHistoryMapper;
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

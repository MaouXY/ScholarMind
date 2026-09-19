package com.maou.apptemplateapi.module.citation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.citation.dto.CitationRecordResponse;
import com.maou.apptemplateapi.module.citation.dto.CitationSaveRequest;
import com.maou.apptemplateapi.module.citation.dto.CitationStyleResponse;
import com.maou.apptemplateapi.module.citation.entity.CitationRecord;
import com.maou.apptemplateapi.module.citation.enums.CitationStyle;
import com.maou.apptemplateapi.module.citation.mapper.CitationRecordMapper;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引用管理：为文献即时生成多格式引用文本，并支持收藏引用与参考文献列表导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CitationService {

    private static final int MAX_EXPORT_RECORDS = 500;
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String UNKNOWN_TITLE = "文献已删除或不可见";

    private final CitationRecordMapper citationRecordMapper;
    private final CitationFormatter citationFormatter;
    private final LiteratureService literatureService;
    private final LiteratureMapper literatureMapper;

    public List<CitationStyleResponse> stylesForLiterature(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "citation-style-list");
        List<CitationStyleResponse> responses = Arrays.stream(CitationStyle.values())
                .map(style -> new CitationStyleResponse(style.name(), style.getLabel(), citationFormatter.format(literature, style)))
                .toList();
        log.info("citation styles generated, scenario=citation-style-list, literatureId={}, userId={}, styleCount={}",
                literatureId, currentUser.getId(), responses.size());
        return responses;
    }

    public PageResult<CitationRecordResponse> list(long page, long size, Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Page<CitationRecord> result = citationRecordMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<CitationRecord>()
                        .eq(CitationRecord::getUserId, currentUser.getId())
                        .eq(literatureId != null, CitationRecord::getLiteratureId, literatureId)
                        .eq(CitationRecord::getDeleted, 0)
                        .orderByDesc(CitationRecord::getCreatedAt)
                        .orderByDesc(CitationRecord::getId));
        Map<Long, Literature> literatureMap = loadLiteratures(result.getRecords().stream()
                .map(CitationRecord::getLiteratureId)
                .distinct()
                .toList());
        List<CitationRecordResponse> items = result.getRecords().stream()
                .map(record -> toResponse(record, title(literatureMap, record.getLiteratureId())))
                .toList();
        return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional
    public CitationRecordResponse save(CitationSaveRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        CitationStyle style = CitationStyle.fromCode(request.style());
        Literature literature = literatureService.requireReadableLiterature(request.literatureId(), currentUser, "citation-save");
        CitationRecord existing = citationRecordMapper.selectOne(new LambdaQueryWrapper<CitationRecord>()
                .eq(CitationRecord::getUserId, currentUser.getId())
                .eq(CitationRecord::getLiteratureId, request.literatureId())
                .eq(CitationRecord::getCitationStyle, style.name())
                .eq(CitationRecord::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            log.warn("citation duplicated, scenario=citation-save, literatureId={}, userId={}, style={}, citationId={}",
                    request.literatureId(), currentUser.getId(), style.name(), existing.getId());
            throw new BusinessException(ErrorCode.CITATION_DUPLICATED);
        }
        CitationRecord record = new CitationRecord();
        record.setUserId(currentUser.getId());
        record.setLiteratureId(literature.getId());
        record.setCitationStyle(style.name());
        record.setCitationText(citationFormatter.format(literature, style));
        record.setNote(request.note());
        citationRecordMapper.insert(record);
        log.info("citation saved, scenario=citation-save, literatureId={}, userId={}, style={}, citationId={}, textLength={}",
                literature.getId(), currentUser.getId(), style.name(), record.getId(), record.getCitationText().length());
        return toResponse(citationRecordMapper.selectById(record.getId()), literature.getTitle());
    }

    @Transactional
    public void delete(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        CitationRecord record = citationRecordMapper.selectById(id);
        if (record == null || record.getDeleted() != 0 || !currentUser.getId().equals(record.getUserId())) {
            log.warn("citation not found or not owned, scenario=citation-delete, citationId={}, userId={}", id, currentUser.getId());
            throw new BusinessException(ErrorCode.CITATION_NOT_FOUND);
        }
        citationRecordMapper.deleteById(id);
        log.info("citation deleted, scenario=citation-delete, citationId={}, userId={}, literatureId={}, style={}",
                id, currentUser.getId(), record.getLiteratureId(), record.getCitationStyle());
    }

    public String exportText(String styleCode) {
        CurrentUser currentUser = CurrentUserProvider.require();
        CitationStyle style = CitationStyle.fromCode(styleCode);
        List<CitationRecord> records = citationRecordMapper.selectList(new LambdaQueryWrapper<CitationRecord>()
                .eq(CitationRecord::getUserId, currentUser.getId())
                .eq(CitationRecord::getCitationStyle, style.name())
                .eq(CitationRecord::getDeleted, 0)
                .orderByAsc(CitationRecord::getCreatedAt)
                .orderByAsc(CitationRecord::getId)
                .last("limit " + MAX_EXPORT_RECORDS));

        StringBuilder builder = new StringBuilder();
        builder.append("参考文献（").append(style.getLabel()).append("）").append(System.lineSeparator());
        builder.append("导出时间: ").append(LocalDateTime.now().format(EXPORT_TIME_FORMAT)).append(System.lineSeparator());
        builder.append("条目数: ").append(records.size()).append(System.lineSeparator());
        if (records.isEmpty()) {
            builder.append(System.lineSeparator()).append("（暂无该格式的引用收藏）");
        } else {
            for (int i = 0; i < records.size(); i++) {
                builder.append(System.lineSeparator())
                        .append('[').append(i + 1).append("] ")
                        .append(records.get(i).getCitationText());
            }
        }
        log.info("citation export generated, scenario=citation-export, userId={}, style={}, recordCount={}",
                currentUser.getId(), style.name(), records.size());
        return builder.toString();
    }

    private Map<Long, Literature> loadLiteratures(List<Long> literatureIds) {
        if (literatureIds.isEmpty()) {
            return Map.of();
        }
        return literatureMapper.selectBatchIds(literatureIds).stream()
                .collect(Collectors.toMap(Literature::getId, Function.identity(), (left, right) -> left));
    }

    private String title(Map<Long, Literature> literatureMap, Long literatureId) {
        Literature literature = literatureMap.get(literatureId);
        if (literature == null || literature.getDeleted() != 0 || literature.getTitle() == null) {
            return UNKNOWN_TITLE;
        }
        return literature.getTitle();
    }

    private CitationRecordResponse toResponse(CitationRecord record, String literatureTitle) {
        CitationStyle style = CitationStyle.fromCode(record.getCitationStyle());
        return new CitationRecordResponse(record.getId(), record.getLiteratureId(), literatureTitle,
                style.name(), style.getLabel(), record.getCitationText(), record.getNote(), record.getCreatedAt());
    }
}

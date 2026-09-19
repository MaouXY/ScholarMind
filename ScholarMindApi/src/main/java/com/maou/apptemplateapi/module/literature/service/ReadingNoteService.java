package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.ReadingNote;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.mapper.ReadingNoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingNoteService {

    private static final int MAX_EXPORT_RECORDS = 500;
    private static final String UNKNOWN_TITLE = "文献已删除或不可见";
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReadingNoteMapper readingNoteMapper;
    private final LiteratureMapper literatureMapper;
    private final LiteratureService literatureService;

    @Transactional
    public ReadingNoteResponse create(ReadingNoteRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(request.literatureId(), currentUser, "reading-note-create");
        ReadingNote note = new ReadingNote();
        note.setUserId(currentUser.getId());
        note.setLiteratureId(request.literatureId());
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setAiGenerated(request.aiGenerated() != null && request.aiGenerated() == 1 ? 1 : 0);
        readingNoteMapper.insert(note);
        log.info("reading note created, scenario=reading-note-create, literatureId={}, userId={}, noteId={}, aiGenerated={}",
                request.literatureId(), currentUser.getId(), note.getId(), note.getAiGenerated());
        return toResponse(readingNoteMapper.selectById(note.getId()), literature.getTitle());
    }

    @Transactional
    public ReadingNoteResponse update(Long id, ReadingNoteRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(request.literatureId(), currentUser, "reading-note-update");
        ReadingNote note = requireOwnedNote(id, currentUser, "reading-note-update");
        note.setLiteratureId(request.literatureId());
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setAiGenerated(request.aiGenerated() != null && request.aiGenerated() == 1 ? 1 : 0);
        readingNoteMapper.updateById(note);
        log.info("reading note updated, scenario=reading-note-update, literatureId={}, userId={}, noteId={}, aiGenerated={}",
                request.literatureId(), currentUser.getId(), id, note.getAiGenerated());
        return toResponse(readingNoteMapper.selectById(id), literature.getTitle());
    }

    @Transactional
    public void delete(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        ReadingNote note = requireOwnedNote(id, currentUser, "reading-note-delete");
        readingNoteMapper.deleteById(id);
        log.info("reading note deleted, scenario=reading-note-delete, literatureId={}, userId={}, noteId={}",
                note.getLiteratureId(), currentUser.getId(), id);
    }

    public PageResult<ReadingNoteResponse> list(Long literatureId, long page, long size) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (literatureId != null) {
            literatureService.requireReadableLiterature(literatureId, currentUser, "reading-note-list");
        }
        Page<ReadingNote> result = readingNoteMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<ReadingNote>()
                        .eq(ReadingNote::getUserId, currentUser.getId())
                        .eq(literatureId != null, ReadingNote::getLiteratureId, literatureId)
                        .eq(ReadingNote::getDeleted, 0)
                        .orderByDesc(ReadingNote::getUpdatedAt)
                        .orderByDesc(ReadingNote::getId));
        Map<Long, Literature> literatureMap = loadLiteratures(result.getRecords().stream()
                .map(ReadingNote::getLiteratureId)
                .distinct()
                .toList());
        return PageResult.of(result.getRecords().stream()
                        .map(note -> toResponse(note, title(literatureMap, note.getLiteratureId())))
                        .toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 阅读笔记导出：把当前用户的笔记按文献分组输出为 Markdown 文本（前端另存为 .md 文件）。
     */
    public String exportMarkdown(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (literatureId != null) {
            literatureService.requireReadableLiterature(literatureId, currentUser, "reading-note-export");
        }
        List<ReadingNote> notes = readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, currentUser.getId())
                .eq(literatureId != null, ReadingNote::getLiteratureId, literatureId)
                .eq(ReadingNote::getDeleted, 0)
                .orderByAsc(ReadingNote::getLiteratureId)
                .orderByDesc(ReadingNote::getUpdatedAt)
                .orderByDesc(ReadingNote::getId)
                .last("limit " + MAX_EXPORT_RECORDS));

        Map<Long, Literature> literatureMap = loadLiteratures(notes.stream()
                .map(ReadingNote::getLiteratureId)
                .distinct()
                .toList());
        Map<Long, List<ReadingNote>> grouped = notes.stream()
                .collect(Collectors.groupingBy(ReadingNote::getLiteratureId, LinkedHashMap::new, Collectors.toList()));

        StringBuilder builder = new StringBuilder();
        builder.append("# 阅读笔记导出").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- 导出时间：").append(LocalDateTime.now().format(EXPORT_TIME_FORMAT)).append(System.lineSeparator());
        builder.append("- 笔记数量：").append(notes.size()).append(System.lineSeparator());
        builder.append("- 涉及文献：").append(grouped.size()).append(" 篇").append(System.lineSeparator());

        if (notes.isEmpty()) {
            builder.append(System.lineSeparator()).append("_暂无阅读笔记_");
        }
        grouped.forEach((key, values) -> {
            builder.append(System.lineSeparator()).append("## ").append(title(literatureMap, key))
                    .append(System.lineSeparator()).append(System.lineSeparator());
            for (ReadingNote note : values) {
                builder.append("### ").append(StringUtils.hasText(note.getTitle()) ? note.getTitle() : "未命名笔记")
                        .append(System.lineSeparator()).append(System.lineSeparator());
                builder.append("- 来源：").append(note.getAiGenerated() != null && note.getAiGenerated() == 1 ? "AI 生成" : "手动记录")
                        .append(System.lineSeparator());
                builder.append("- 更新时间：").append(note.getUpdatedAt() == null ? "-" : note.getUpdatedAt().format(EXPORT_TIME_FORMAT))
                        .append(System.lineSeparator()).append(System.lineSeparator());
                builder.append(note.getContent() == null ? "" : note.getContent())
                        .append(System.lineSeparator()).append(System.lineSeparator());
            }
        });
        log.info("reading note export generated, scenario=reading-note-export, userId={}, literatureId={}, noteCount={}, literatureCount={}",
                currentUser.getId(), literatureId, notes.size(), grouped.size());
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
        if (literature == null || literature.getDeleted() != 0 || !StringUtils.hasText(literature.getTitle())) {
            return UNKNOWN_TITLE;
        }
        return literature.getTitle();
    }

    public List<ReadingNoteResponse> recentNotes(Long literatureId, Long userId, int limit) {
        if (literatureId == null || userId == null) {
            return List.of();
        }
        return readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                        .eq(ReadingNote::getUserId, userId)
                        .eq(ReadingNote::getLiteratureId, literatureId)
                        .eq(ReadingNote::getDeleted, 0)
                        .orderByDesc(ReadingNote::getUpdatedAt)
                        .orderByDesc(ReadingNote::getId)
                        .last("limit " + Math.max(limit, 1)))
                .stream()
                .map(note -> toResponse(note, null))
                .toList();
    }

    private ReadingNote requireOwnedNote(Long id, CurrentUser currentUser, String scenario) {
        ReadingNote note = readingNoteMapper.selectById(id);
        if (note == null || note.getDeleted() != 0) {
            log.warn("reading note not found, scenario={}, noteId={}, userId={}", scenario, id, currentUser.getId());
            throw new BusinessException(ErrorCode.READING_NOTE_NOT_FOUND);
        }
        if (!currentUser.getId().equals(note.getUserId())) {
            log.warn("reading note access denied, scenario={}, noteId={}, userId={}, ownerId={}",
                    scenario, id, currentUser.getId(), note.getUserId());
            throw new BusinessException(ErrorCode.READING_NOTE_ACCESS_DENIED);
        }
        return note;
    }

    private ReadingNoteResponse toResponse(ReadingNote note, String literatureTitle) {
        return new ReadingNoteResponse(note.getId(), note.getLiteratureId(), literatureTitle, note.getTitle(), note.getContent(),
                note.getAiGenerated(), note.getCreatedAt(), note.getUpdatedAt());
    }
}

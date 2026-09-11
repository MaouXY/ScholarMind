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
import com.maou.apptemplateapi.module.literature.entity.ReadingNote;
import com.maou.apptemplateapi.module.literature.mapper.ReadingNoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingNoteService {

    private final ReadingNoteMapper readingNoteMapper;
    private final LiteratureService literatureService;

    @Transactional
    public ReadingNoteResponse create(ReadingNoteRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        literatureService.requireReadableLiterature(request.literatureId(), currentUser, "reading-note-create");
        ReadingNote note = new ReadingNote();
        note.setUserId(currentUser.getId());
        note.setLiteratureId(request.literatureId());
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setAiGenerated(request.aiGenerated() != null && request.aiGenerated() == 1 ? 1 : 0);
        readingNoteMapper.insert(note);
        log.info("reading note created, scenario=reading-note-create, literatureId={}, userId={}, noteId={}, aiGenerated={}",
                request.literatureId(), currentUser.getId(), note.getId(), note.getAiGenerated());
        return toResponse(readingNoteMapper.selectById(note.getId()));
    }

    @Transactional
    public ReadingNoteResponse update(Long id, ReadingNoteRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        literatureService.requireReadableLiterature(request.literatureId(), currentUser, "reading-note-update");
        ReadingNote note = requireOwnedNote(id, currentUser, "reading-note-update");
        note.setLiteratureId(request.literatureId());
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setAiGenerated(request.aiGenerated() != null && request.aiGenerated() == 1 ? 1 : 0);
        readingNoteMapper.updateById(note);
        log.info("reading note updated, scenario=reading-note-update, literatureId={}, userId={}, noteId={}, aiGenerated={}",
                request.literatureId(), currentUser.getId(), id, note.getAiGenerated());
        return toResponse(readingNoteMapper.selectById(id));
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
        return PageResult.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
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
                .map(this::toResponse)
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

    private ReadingNoteResponse toResponse(ReadingNote note) {
        return new ReadingNoteResponse(note.getId(), note.getLiteratureId(), note.getTitle(), note.getContent(),
                note.getAiGenerated(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
